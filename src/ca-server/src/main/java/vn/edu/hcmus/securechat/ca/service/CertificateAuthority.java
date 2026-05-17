package vn.edu.hcmus.securechat.ca.service;

import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.edu.hcmus.securechat.common.crypto.KeyStoreManager;
import vn.edu.hcmus.securechat.common.exception.PkiException;

/**
 * CertificateAuthority — Xử lý logic cấp chứng chỉ X.509 v3.
 *
 * Chức năng:
 * - Kiểm tra CSR hợp lệ
 * - Cấp chứng chỉ X.509 v3 (RSA-2048)
 * - Ký chứng chỉ bằng CA Private Key
 * - Thiết lập các extension bắt buộc (KeyUsage, ExtendedKeyUsage, BasicConstraints)
 */
public class CertificateAuthority {

    private static final Logger log = LoggerFactory.getLogger(CertificateAuthority.class);

    private static final String CA_ALIAS = "securechat-ca";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA"; // RSA-PSS mặc dù tốt hơn nhưng BouncyCastle v1.78 hỗ trợ đầy đủ
    private static final int CERTIFICATE_VALIDITY_DAYS = 365;
    private static final int CERTIFICATE_VALIDITY_MS = CERTIFICATE_VALIDITY_DAYS * 24 * 60 * 60 * 1000;

    private PrivateKey caPrivateKey;
    private X509Certificate caCertificate;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public CertificateAuthority() throws KeyStoreException, NoSuchAlgorithmException {
        initializeCaKeys();
    }

    /**
     * Khởi tạo CA Private Key từ Windows KeyStore.
     * Nếu alias "securechat-ca" chưa tồn tại, sẽ throw exception.
     * (Trong production, admin phải tạo sẵn cặp khóa CA qua certutil hoặc OpenSSL)
     */
    private void initializeCaKeys() throws KeyStoreException, NoSuchAlgorithmException {
        KeyStore ks = KeyStoreManager.loadPersonalStore();
        
        if (!ks.containsAlias(CA_ALIAS)) {
            throw new KeyStoreException(
                "CA Private Key alias '" + CA_ALIAS + "' not found in Windows-MY KeyStore. " +
                "Please create CA certificate first using: certutil -importpfx ca-cert.pfx"
            );
        }

        try {
            caPrivateKey = (PrivateKey) ks.getKey(CA_ALIAS, null);
            caCertificate = (X509Certificate) ks.getCertificate(CA_ALIAS);

            log.info("Loaded CA certificate: CN={}, NotBefore={}, NotAfter={}",
                caCertificate.getSubjectX500Principal().getName(),
                caCertificate.getNotBefore(),
                caCertificate.getNotAfter()
            );
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new KeyStoreException("Failed to load CA key/cert from Windows-MY", e);
        }
    }

    /**
     * Cấp chứng chỉ X.509 v3 mới cho subject.
     *
     * @param subjectDn X.500 DN của subject (ví dụ: "CN=user@example.com,O=Company,C=US")
     * @param subjectPublicKey Public Key của subject (RSA)
     * @return DER-encoded X.509 certificate
     */
    public byte[] issueCertificate(String subjectDn, PublicKey subjectPublicKey)
            throws PkiException {

        try {
            // Sinh serial number ngẫu nhiên (positive BigInteger 160-bit)
            BigInteger serialNumber = generateSerialNumber();

            // Thời gian hiệu lực
            long now = System.currentTimeMillis();
            Date notBefore = new Date(now);
            Date notAfter = new Date(now + CERTIFICATE_VALIDITY_MS);

            // Tạo builder
            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                caCertificate.getSubjectX500Principal(),
                serialNumber,
                notBefore,
                notAfter,
                new javax.security.auth.x500.X500Principal(subjectDn),
                subjectPublicKey
            );

            // Thêm extensions
            addExtensions(certBuilder);

            // Ký chứng chỉ (Sử dụng SunMSCAPI để hỗ trợ key của Windows)
            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .setProvider("SunMSCAPI")
                    .build(caPrivateKey);

            // Convert sang X.509Certificate
            X509Certificate cert = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certBuilder.build(signer));

            log.info("Issued certificate: serial={}, subject={}, valid={}..{}",
                    serialNumber.toString(16),
                    subjectDn,
                    notBefore,
                    notAfter
            );

            return cert.getEncoded();

        } catch (CertIOException | OperatorCreationException | CertificateException e) {
            throw new PkiException("Failed to issue certificate", e);
        }
    }

    /**
     * Thêm các X.509 v3 extensions bắt buộc vào chứng chỉ.
     *
     * Theo RFC 5280 và quy chuẩn dự án:
     * - KeyUsage: Xác định mục đích sử dụng khóa
     * - ExtendedKeyUsage: Xác định ứng dụng cụ thể (TLS, Kerberos, etc.)
     * - BasicConstraints: Phân biệt CA vs End Entity
     * - SubjectAlternativeName: Tên phụ (ví dụ: email, DNS)
     */
    private void addExtensions(X509v3CertificateBuilder certBuilder) throws CertIOException {
        // KeyUsage extension (RFC 5280 section 4.2.1.3)
        // Bit 0: digitalSignature
        // Bit 1: nonRepudiation
        // Bit 2: keyEncipherment
        // Bit 3: dataEncipherment
        // ...
        int keyUsageBits = KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
        certBuilder.addExtension(
            org.bouncycastle.asn1.x509.Extension.keyUsage,
            true,  // critical
            new KeyUsage(keyUsageBits)
        );

        // BasicConstraints (phân biệt CA vs end entity)
        certBuilder.addExtension(
            org.bouncycastle.asn1.x509.Extension.basicConstraints,
            true,  // critical
            new BasicConstraints(false) // false = not CA
        );

        // ExtendedKeyUsage (xác định ứng dụng cụ thể)
        // Sử dụng serverAuth + clientAuth để tương thích TLS
        certBuilder.addExtension(
            org.bouncycastle.asn1.x509.Extension.extendedKeyUsage,
            false, // not critical
            new ExtendedKeyUsage(new KeyPurposeId[]{
                KeyPurposeId.id_kp_serverAuth,
                KeyPurposeId.id_kp_clientAuth
            })
        );
    }

    /**
     * Sinh serial number ngẫu nhiên (160-bit positive integer).
     * Đảm bảo uniqueness và ngăn chặn collision attacks.
     */
    private BigInteger generateSerialNumber() {
        SecureRandom random = new SecureRandom();
        byte[] serialBytes = new byte[20]; // 160 bits
        random.nextBytes(serialBytes);

        // Đảm bảo positive (bit cao nhất = 0)
        serialBytes[0] = (byte) (serialBytes[0] & 0x7F);

        BigInteger serial = new BigInteger(1, serialBytes);
        if (serial.signum() <= 0) {
            serial = new BigInteger("1234567890");
        }

        return serial;
    }

    /**
     * Lấy CA certificate (dùng để gửi cùng với issued cert).
     */
    public X509Certificate getCaCertificate() {
        return caCertificate;
    }

    /**
     * Lấy CA private key (dùng cho OCSP signing).
     */
    public PrivateKey getCaPrivateKey() {
        return caPrivateKey;
    }

    /**
     * Lấy CA certificate chain (dùng cho certificate chain validation).
     */
    public X509Certificate[] getCertificateChain() throws KeyStoreException {
        KeyStore ks = KeyStoreManager.loadPersonalStore();
        java.security.cert.Certificate[] certChain = ks.getCertificateChain(CA_ALIAS);
        if (certChain == null) {
            return new X509Certificate[]{caCertificate};
        }
        X509Certificate[] x509Chain = new X509Certificate[certChain.length];
        for (int i = 0; i < certChain.length; i++) {
            x509Chain[i] = (X509Certificate) certChain[i];
        }
        return x509Chain;
    }
}
