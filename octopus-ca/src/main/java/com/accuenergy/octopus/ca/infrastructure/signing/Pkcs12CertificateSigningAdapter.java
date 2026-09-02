package com.accuenergy.octopus.ca.infrastructure.signing;

import com.accuenergy.octopus.ca.application.CertificateSigningPort;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development/private-CA adapter. Production replaces this port with HSM/KMS signing. */
@Component
@ConditionalOnProperty(prefix = "octopus.ca.signing", name = "mode", havingValue = "local-pkcs12")
public final class Pkcs12CertificateSigningAdapter implements CertificateSigningPort {
    private final PrivateKey issuerPrivateKey;
    private final X509Certificate issuerCertificate;
    private final SecureRandom random = new SecureRandom();

    public Pkcs12CertificateSigningAdapter(
            @Value("${octopus.ca.signing.key-store}") String keyStorePath,
            @Value("${octopus.ca.signing.key-store-password}") String keyStorePassword,
            @Value("${octopus.ca.signing.key-alias}") String keyAlias) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(Path.of(keyStorePath))) {
                store.load(input, keyStorePassword.toCharArray());
            }
            issuerPrivateKey = (PrivateKey) store.getKey(keyAlias, keyStorePassword.toCharArray());
            issuerCertificate = (X509Certificate) store.getCertificate(keyAlias);
            if (issuerPrivateKey == null || issuerCertificate == null) {
                throw new IllegalArgumentException("Signing alias does not contain a private key and certificate");
            }
        } catch (Exception invalidStore) {
            throw new IllegalStateException("Unable to load local CA signing key", invalidStore);
        }
    }

    @Override
    public IssuedCertificate sign(SigningRequest request) {
        try {
            BouncyCastleProvider provider = new BouncyCastleProvider();
            PKCS10CertificationRequest csr = new PKCS10CertificationRequest(request.certificateSigningRequest());
            var publicKey = new JcaPKCS10CertificationRequest(csr).setProvider(provider).getPublicKey();
            BigInteger serial = new BigInteger(159, random).setBit(158);
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuerCertificate, serial, Date.from(request.notBefore()), Date.from(request.notAfter()),
                    csr.getSubject(), publicKey);
            JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    extensions.createSubjectKeyIdentifier(publicKey));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extensions.createAuthorityKeyIdentifier(issuerCertificate));
            GeneralName[] names = request.subjectAlternativeNames().stream()
                    .map(value -> new GeneralName(GeneralName.uniformResourceIdentifier, value))
                    .toArray(GeneralName[]::new);
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names));
            String algorithm = issuerPrivateKey.getAlgorithm().equalsIgnoreCase("EC")
                    ? "SHA256withECDSA" : "SHA256withRSA";
            var signer = new JcaContentSignerBuilder(algorithm).setProvider(provider).build(issuerPrivateKey);
            X509Certificate certificate = new JcaX509CertificateConverter().setProvider(provider)
                    .getCertificate(builder.build(signer));
            certificate.verify(issuerCertificate.getPublicKey());
            return new IssuedCertificate(UUID.randomUUID(), serial.toString(16), pemChain(certificate, issuerCertificate),
                    request.notBefore(), request.notAfter());
        } catch (Exception signingFailure) {
            throw new IllegalStateException("Unable to sign operational certificate", signingFailure);
        }
    }

    private static byte[] pemChain(X509Certificate certificate, X509Certificate issuer) throws Exception {
        StringWriter output = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(output)) {
            writer.writeObject(certificate);
            writer.writeObject(issuer);
        }
        return output.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
