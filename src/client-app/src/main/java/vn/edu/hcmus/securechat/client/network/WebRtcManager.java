package vn.edu.hcmus.securechat.client.network;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.client.crypto.E2eeCryptoService;
import vn.edu.hcmus.securechat.common.crypto.AesGcmCipher;
import vn.edu.hcmus.securechat.common.exception.CryptoException;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.CallSignalDto;

/**
 * Quản lý tính năng Gọi thoại (Audio Call) và Gọi video (Video Call).
 *
 * <h3>Kiến trúc Signaling (E2EE over Chat Server)</h3>
 * <p>Tất cả tín hiệu thiết lập cuộc gọi (SDP Offer, SDP Answer, ICE Candidates)
 * đều được bọc trong {@link CallSignalDto} và mã hóa E2EE (Double Ratchet)
 * trước khi gửi qua Chat Server. Server forward mù — không biết nội dung.</p>
 *
 * <h3>Media Stream (Peer-to-Peer qua Java Sound API)</h3>
 * <p>Do đây là ứng dụng Desktop Swing thuần Java, tầng Media sử dụng
 * {@code javax.sound.sampled} để capture/playback âm thanh PCM 16kHz mono.
 * Mỗi gói âm thanh được mã hóa thêm một lớp AES-256-GCM (MediaKey) trước khi
 * gửi qua kết nối TCP hiện tại. Điều này đảm bảo nội dung cuộc gọi được mã hóa
 * đầu cuối (E2EE Media) ngay cả khi đang trung chuyển qua Chat Server.</p>
 *
 * <h3>Trạng thái cuộc gọi</h3>
 * <pre>
 * IDLE → RINGING (caller) → CONNECTING → ACTIVE
 *                 ↓ (callee)
 *              RINGING → ACTIVE
 *                       ↓ (any side)
 *                      ENDED
 * </pre>
 */
public class WebRtcManager {

    private static final Logger log = LoggerFactory.getLogger(WebRtcManager.class);

    /** Tần số PCM âm thanh. */
    private static final float SAMPLE_RATE = 16000.0f;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final int AUDIO_BUFFER_SIZE = 1024;

    public enum CallState { IDLE, RINGING, CONNECTING, ACTIVE, ENDED }
    public enum MediaType { AUDIO, VIDEO }

    /** Metadata theo dõi cuộc gọi đang hoạt động. */
    public record ActiveCall(String callId, String peerId, MediaType mediaType, boolean isCaller, CallState state) {}

    private final String localUserId;
    private final E2eeCryptoService e2ee;

    private volatile ActiveCall activeCall = null;
    private volatile Thread captureThread = null;
    private volatile Thread playbackThread = null;
    private volatile SourceDataLine speaker = null;
    private volatile boolean micMuted = false;

    // MediaKey: AES-256-GCM key để mã hóa luồng âm thanh P2P
    private volatile byte[] mediaKey = null;

    /** Callback khi nhận SDP Offer/Answer (trao đổi media session). */
    private Consumer<CallSignalDto> onSignalReceived;

    /** Callback thay đổi trạng thái cuộc gọi. */
    private Consumer<CallState> onStateChanged;

    /** Callback khi cuộc gọi bị từ chối hoặc kết thúc từ phía peer. */
    private Consumer<String> onCallEnded;

    /** Lưu MediaKey của outbound/inbound theo callId. */
    private final Map<String, byte[]> mediaKeys = new ConcurrentHashMap<>();

    public WebRtcManager(String localUserId, E2eeCryptoService e2ee) {
        this.localUserId = localUserId;
        this.e2ee = e2ee;
    }

    // =========================================================================
    // === Setters cho callbacks ================================================
    // =========================================================================

    public void setOnSignalReceived(Consumer<CallSignalDto> callback) { this.onSignalReceived = callback; }
    public void setOnStateChanged(Consumer<CallState> callback) { this.onStateChanged = callback; }
    public void setOnCallEnded(Consumer<String> callback) { this.onCallEnded = callback; }
    public void setMicMuted(boolean muted) { this.micMuted = muted; }
    public boolean isMicMuted() { return micMuted; }

    // =========================================================================
    // === CALLER SIDE: Khởi tạo cuộc gọi =====================================
    // =========================================================================

    /**
     * Bắt đầu cuộc gọi tới peer.
     *
     * <p>Gửi SDP Offer (chứa MediaKey E2EE encrypt) tới peer qua Chat Server.</p>
     *
     * @param peerId    ID người cần gọi
     * @param mediaType AUDIO hoặc VIDEO
     * @throws IOException Lỗi mạng
     */
    public void startCall(String peerId, MediaType mediaType) throws IOException {
        if (activeCall != null && activeCall.state() != CallState.ENDED) {
            throw new IllegalStateException("Already in a call");
        }

        String callId = UUID.randomUUID().toString();

        // Sinh MediaKey cho cuộc gọi này
        byte[] mk = new byte[32];
        new SecureRandom().nextBytes(mk);
        mediaKeys.put(callId, mk);

        // SDP Offer plaintext: embed MediaKey dưới dạng base64 trong "SDP"
        String sdpOfferJson = buildOfferJson(callId, mediaType, mk);

        sendSignal(callId, "SDP_OFFER", peerId, sdpOfferJson, mediaType);

        activeCall = new ActiveCall(callId, peerId, mediaType, true, CallState.RINGING);
        notifyStateChanged(CallState.RINGING);
        log.info("CALL_STARTED callId={} to={} type={}", callId, peerId, mediaType);
    }

    /**
     * Kết thúc cuộc gọi (caller hoặc callee đều dùng được).
     */
    public void endCall() throws IOException {
        if (activeCall == null) return;
        String callId = activeCall.callId();
        String peerId = activeCall.peerId();

        stopMediaStreams();

        // Gửi tín hiệu END (dùng ICE_CANDIDATE với signalType = "CALL_END")
        sendSignal(callId, "CALL_END", peerId, "{\"reason\":\"user_ended\"}", activeCall.mediaType());

        cleanupCall(callId);
        notifyStateChanged(CallState.ENDED);
        log.info("CALL_ENDED callId={}", callId);
    }

    // =========================================================================
    // === CALLEE SIDE: Xử lý tín hiệu đến ====================================
    // =========================================================================

    /**
     * Xử lý {@link CallSignalDto} nhận được từ Chat Server.
     *
     * @param signal Tín hiệu đã được giải mã E2EE
     */
    public void handleIncomingSignal(CallSignalDto signal) {
        if ("AUDIO_FRAME".equals(signal.getSignalType())) {
            playAudioFrame(signal.getCallId(), signal.getEncryptedSignal());
            return;
        }

        log.info("CALL_SIGNAL_RECEIVED callId={} type={} from={}",
                signal.getCallId(), signal.getSignalType(), signal.getCallerId());

        switch (signal.getSignalType()) {
            case "SDP_OFFER" -> handleSdpOffer(signal);
            case "SDP_ANSWER" -> handleSdpAnswer(signal);
            case "CALL_END" -> handleCallEnd(signal);
            default -> log.warn("Unknown signal type: {}", signal.getSignalType());
        }

        if (onSignalReceived != null) {
            onSignalReceived.accept(signal);
        }
    }

    /**
     * Chấp nhận cuộc gọi (Callee gọi sau khi user nhấn Accept).
     *
     * @param signal SDP Offer đã nhận
     */
    public void acceptCall(CallSignalDto signal) throws IOException {
        String callId = signal.getCallId();
        String callerId = signal.getCallerId();
        MediaType mediaType = "VIDEO".equalsIgnoreCase(signal.getMediaType())
                ? MediaType.VIDEO : MediaType.AUDIO;

        // Trích xuất MediaKey từ SDP Offer đã giải mã
        byte[] mk = extractMediaKey(signal.getEncryptedSignal());
        if (mk != null) {
            mediaKeys.put(callId, mk);
        }

        activeCall = new ActiveCall(callId, callerId, mediaType, false, CallState.CONNECTING);
        notifyStateChanged(CallState.CONNECTING);

        // Gửi SDP Answer
        String sdpAnswer = "{\"type\":\"answer\",\"accepted\":true,\"callId\":\"" + callId + "\"}";
        sendSignal(callId, "SDP_ANSWER", callerId, sdpAnswer, mediaType);

        // Bắt đầu media stream
        startMediaStreams(callId);

        activeCall = new ActiveCall(callId, callerId, mediaType, false, CallState.ACTIVE);
        notifyStateChanged(CallState.ACTIVE);
        log.info("CALL_ACCEPTED callId={} from={}", callId, callerId);
    }

    /**
     * Từ chối cuộc gọi.
     */
    public void rejectCall(CallSignalDto signal) throws IOException {
        String callId = signal.getCallId();
        String callerId = signal.getCallerId();
        sendSignal(callId, "CALL_END", callerId, "{\"reason\":\"rejected\"}", MediaType.AUDIO);
        log.info("CALL_REJECTED callId={}", callId);
    }

    // =========================================================================
    // === PRIVATE HELPERS =====================================================
    // =========================================================================

    private void handleSdpOffer(CallSignalDto signal) {
        // Thông báo cho UI layer rằng có cuộc gọi đến
        notifyStateChanged(CallState.RINGING);
    }

    private void handleSdpAnswer(CallSignalDto signal) {
        if (activeCall != null && activeCall.callId().equals(signal.getCallId())) {
            activeCall = new ActiveCall(
                    signal.getCallId(), signal.getCallerId(),
                    activeCall.mediaType(), true, CallState.ACTIVE);
            notifyStateChanged(CallState.ACTIVE);

            // Caller bắt đầu stream sau khi nhận Answer
            try {
                startMediaStreams(signal.getCallId());
            } catch (Exception ex) {
                log.error("Failed to start media streams after SDP_ANSWER", ex);
            }
        }
    }

    private void handleCallEnd(CallSignalDto signal) {
        stopMediaStreams();
        cleanupCall(signal.getCallId());
        notifyStateChanged(CallState.ENDED);
        if (onCallEnded != null) {
            onCallEnded.accept(signal.getCallerId());
        }
    }

    private void sendSignal(String callId, String signalType, String peerId,
                             String signalPayloadJson, MediaType mediaType) throws IOException {
        // E2EE encrypt payload qua Double Ratchet trước khi đưa vào CallSignalDto
        String encryptedSignal;
        try {
            vn.edu.hcmus.securechat.common.protocol.dto.EncryptedChatEnvelope envelope =
                    e2ee.encryptForPeer(peerId, signalPayloadJson);
            encryptedSignal = JsonSerializer.toJsonString(envelope);
        } catch (Exception ex) {
            throw new IOException("Failed to E2EE-encrypt call signal", ex);
        }

        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);

        CallSignalDto dto = new CallSignalDto(
                callId, signalType, localUserId, peerId,
                encryptedSignal, mediaType.name(),
                Instant.now().getEpochSecond(),
                Base64.getEncoder().encodeToString(nonceBytes));

        byte frameType = switch (signalType) {
            case "SDP_OFFER" -> PacketFrame.TYPE_CALL_SDP_OFFER;
            case "SDP_ANSWER" -> PacketFrame.TYPE_CALL_SDP_ANSWER;
            default -> PacketFrame.TYPE_CALL_ICE_CANDIDATE;
        };

        try {
            e2ee.sendFrame(frameType, JsonSerializer.toBytes(dto));
        } catch (vn.edu.hcmus.securechat.common.exception.ProtocolException pe) {
            throw new IOException("Failed to serialize CallSignalDto", pe);
        }
        log.debug("CALL_SIGNAL_SENT callId={} type={} to={}", callId, signalType, peerId);
    }

    /**
     * Bắt đầu luồng capture (microphone) và playback (speaker).
     * Mỗi gói âm thanh được mã hóa AES-256-GCM bằng MediaKey.
     */
    private void startMediaStreams(String callId) {
        this.mediaKey = mediaKeys.get(callId);
        if (this.mediaKey == null) {
            log.warn("No MediaKey for callId={} — media will NOT be encrypted", callId);
            return;
        }

        AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, true, false);

        // Khởi tạo speaker phát âm thanh liên tục
        try {
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
            if (AudioSystem.isLineSupported(speakerInfo)) {
                speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
                speaker.open(format);
                speaker.start();
                log.info("Speaker playback started callId={}", callId);
            }
        } catch (Exception ex) {
            log.error("Failed to initialize speaker", ex);
        }

        // Capture thread: microphone → encrypt → gửi
        captureThread = new Thread(() -> {
            try {
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                if (!AudioSystem.isLineSupported(info)) {
                    log.warn("Microphone not supported on this system");
                    return;
                }
                TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(format);
                microphone.start();
                log.info("Microphone capture started callId={}", callId);

                byte[] buffer = new byte[AUDIO_BUFFER_SIZE];
                while (!Thread.currentThread().isInterrupted() && microphone.isOpen()) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && mediaKey != null) {
                        if (micMuted) {
                            java.util.Arrays.fill(buffer, 0, bytesRead, (byte) 0);
                        }
                        try {
                            byte[] chunk = java.util.Arrays.copyOf(buffer, bytesRead);
                            byte[] encrypted = AesGcmCipher.encrypt(mediaKey, chunk);
                            // Gửi âm thanh mã hóa qua CallSignalDto dạng ICE_CANDIDATE
                            // (tái dụng kênh signaling để gửi media frame)
                            if (e2ee.getChatSocket() != null && !e2ee.getChatSocket().isClosed()
                                     && activeCall != null) {
                                CallSignalDto audioFrame = new CallSignalDto(
                                        callId, "AUDIO_FRAME", localUserId, activeCall.peerId(),
                                        Base64.getEncoder().encodeToString(encrypted),
                                        "AUDIO", Instant.now().getEpochSecond(), "");
                                e2ee.sendFrame(PacketFrame.TYPE_CALL_ICE_CANDIDATE,
                                        JsonSerializer.toBytes(audioFrame));
                            }
                        } catch (CryptoException | IOException | vn.edu.hcmus.securechat.common.exception.ProtocolException ex) {
                            if (!Thread.currentThread().isInterrupted()) {
                                log.debug("Audio frame send error: {}", ex.getMessage());
                            }
                        }
                    }
                }
                microphone.close();
                log.info("Microphone capture stopped callId={}", callId);
            } catch (Exception ex) {
                log.error("Capture thread error callId={}", callId, ex);
            }
        }, "audio-capture-" + callId);
        captureThread.setDaemon(true);
        captureThread.start();

        log.info("Media streams initialized callId={}", callId);
    }

    /**
     * Phát một gói âm thanh mã hóa nhận được từ peer.
     */
    public void playAudioFrame(String callId, String encryptedBase64) {
        byte[] mk = mediaKeys.get(callId);
        if (mk == null) return;

        try {
            byte[] cipherData = Base64.getDecoder().decode(encryptedBase64);
            byte[] pcmData = AesGcmCipher.decrypt(mk, cipherData);

            SourceDataLine localSpeaker = speaker;
            if (localSpeaker != null && localSpeaker.isOpen()) {
                localSpeaker.write(pcmData, 0, pcmData.length);
            }
        } catch (Exception ex) {
            log.debug("playAudioFrame error: {}", ex.getMessage());
        }
    }

    private void stopMediaStreams() {
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        if (playbackThread != null) {
            playbackThread.interrupt();
            playbackThread = null;
        }
        if (speaker != null) {
            try {
                speaker.stop();
                speaker.close();
            } catch (Exception ignored) {}
            speaker = null;
        }
        log.info("Media streams stopped");
    }

    private void cleanupCall(String callId) {
        byte[] mk = mediaKeys.remove(callId);
        if (mk != null) java.util.Arrays.fill(mk, (byte) 0);
        if (mediaKey != null) java.util.Arrays.fill(mediaKey, (byte) 0);
        mediaKey = null;
        activeCall = null;
        micMuted = false;
    }

    private void notifyStateChanged(CallState state) {
        if (onStateChanged != null) {
            onStateChanged.accept(state);
        }
    }

    private String buildOfferJson(String callId, MediaType mediaType, byte[] mediaKey) {
        // SDP Offer JSON chứa MediaKey encode base64 — sẽ được E2EE trước khi gửi
        return "{\"type\":\"offer\",\"callId\":\"" + callId
                + "\",\"mediaType\":\"" + mediaType.name()
                + "\",\"mediaKey\":\"" + Base64.getEncoder().encodeToString(mediaKey) + "\"}";
    }

    private byte[] extractMediaKey(String encryptedSignalJson) {
        // encryptedSignalJson là chuỗi đã giải mã E2EE (plaintext JSON từ callSignalDto.encryptedSignal)
        // Sau khi giải mã Double Ratchet ta có JSON gốc, extract mediaKey
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    JsonSerializer.getMapper().readTree(encryptedSignalJson);
            com.fasterxml.jackson.databind.JsonNode mkNode = node.get("mediaKey");
            if (mkNode != null && !mkNode.isNull()) {
                return Base64.getDecoder().decode(mkNode.asText());
            }
        } catch (Exception ex) {
            log.debug("extractMediaKey: {}", ex.getMessage());
        }
        return null;
    }

    public ActiveCall getActiveCall() { return activeCall; }
    public boolean isInCall() { return activeCall != null && activeCall.state() != CallState.ENDED; }
}
