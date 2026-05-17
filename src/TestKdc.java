import java.net.Socket;
import java.io.InputStream;
import vn.edu.hcmus.securechat.common.protocol.PacketFrame;
import vn.edu.hcmus.securechat.common.protocol.dto.TgtRequest;
import vn.edu.hcmus.securechat.common.protocol.JsonSerializer;

public class TestKdc {
    public static void main(String[] args) throws Exception {
        System.out.println("Connecting to KDC...");
        Socket s = new Socket("127.0.0.1", 8881);
        
        TgtRequest req = new TgtRequest();
        req.setClientId("test");
        req.setTargetTgs("TGS_SERVER");
        req.setNonce("nonce123");
        // Mock valid cert base64? No, if we send an invalid base64, Base64.decode throws IllegalArgumentException.
        // If we send a valid base64 but invalid cert, CertificateFactory throws CertificateException.
        // Let's see what happens if we send invalid cert.
        req.setCert("YWJjZA=="); // "abcd"
        
        System.out.println("Sending TGT_REQUEST...");
        PacketFrame.write(s.getOutputStream(), PacketFrame.TYPE_TGT_REQUEST, JsonSerializer.toBytes(req));
        
        System.out.println("Reading response...");
        try {
            PacketFrame resp = PacketFrame.read(s.getInputStream());
            System.out.println("Got response type: " + resp.getType());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
