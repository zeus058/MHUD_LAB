package vn.edu.hcmus.securechat.client.network;

import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.protocol.MessageType;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;

/**
 * Socket Client - Thực hiện kết nối TCP thật tới Server.
 */
public class SocketClient {
    private static final Logger log = LoggerFactory.getLogger(SocketClient.class);

    /**
     * Gửi request qua mạng và nhận response từ Server.
     */
    public static PacketFrame sendRequest(String host, int port, PacketFrame request) throws Exception {
        MessageType type = MessageType.fromByte(request.getType());
        log.info("Sending request {} to {}:{}", type, host, port);
        
        try (Socket socket = new Socket(host, port)) {
            // Đặt timeout 10 giây để tránh treo ứng dụng nếu mạng lỗi
            socket.setSoTimeout(10000);
            
            // Gửi dữ liệu đi
            PacketFrame.write(socket.getOutputStream(), request.getType(), request.getPayload());
            
            // Nhận dữ liệu về
            PacketFrame response = PacketFrame.read(socket.getInputStream());
            
            log.info("Received response for {}", type);
            return response;
        }
    }
}
