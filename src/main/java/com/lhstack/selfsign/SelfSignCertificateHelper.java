package com.lhstack.selfsign;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.IPAddress;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Calendar;
import java.util.Date;

public class SelfSignCertificateHelper {

    private static final String BC_PROVIDER = "BC";

    static {
        Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (provider == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * CA PEM  Gen Self 证书
     *
     * @param caCertificatePemPath ca证书文件
     * @param caPrivateKeyPemPath  ca私钥pem文件
     * @param selfSignConfig       自签名配置
     * @return {@link SelfSignCertificateEntity}
     * @throws Exception 例外
     */
    public static SelfSignCertificateEntity genSelfCertificateFromCaPemFile(
            String caCertificatePemPath,
            String caPrivateKeyPemPath,
            SelfSignConfig selfSignConfig) throws Exception {
        try (FileInputStream caPublicKeyPemStream = new FileInputStream(caCertificatePemPath)) {
            X509Certificate caCertificate = (X509Certificate) CertificateFactory.getInstance("X.509", "BC").generateCertificate(caPublicKeyPemStream);
            String certificateAlgorithm = caCertificate.getPublicKey().getAlgorithm();
            KeyFactory keyFactory = KeyFactory.getInstance(certificateAlgorithm, "BC");
            FileReader fileReader = new FileReader(caPrivateKeyPemPath);
            PEMParser pemParser = new PEMParser(fileReader);
            Object o = pemParser.readObject();
            PrivateKey privateKey = null;
            if (o instanceof PEMKeyPair) {
                privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(((PEMKeyPair) o).getPrivateKeyInfo().getEncoded()));
            }
            fileReader.close();
            return genSelfCertificateFromCa(selfSignConfig, caCertificate, privateKey);
        }
    }


    /**
     * CA PEM  Gen Self 证书
     *
     * @param caCertificatePemContent ca证书内容
     * @param caPrivateKeyPemContent  ca私钥pem内容
     * @param selfSignConfig          自签名配置
     * @return {@link SelfSignCertificateEntity}
     * @throws Exception 例外
     */
    public static SelfSignCertificateEntity genSelfCertificateFromCaPem(String caCertificatePemContent,
                                                                        String caPrivateKeyPemContent,
                                                                        SelfSignConfig selfSignConfig) throws Exception {
        ByteArrayInputStream caPublicKeyPemStream = new ByteArrayInputStream(caCertificatePemContent.getBytes());
        X509Certificate caCertificate = (X509Certificate) CertificateFactory.getInstance("X.509", "BC").generateCertificate(caPublicKeyPemStream);
        String certificateAlgorithm = caCertificate.getPublicKey().getAlgorithm();
        KeyFactory keyFactory = KeyFactory.getInstance(certificateAlgorithm, "BC");
        PEMParser pemParser = new PEMParser(new StringReader(caPrivateKeyPemContent));
        Object o = pemParser.readObject();
        PrivateKey privateKey = null;
        if (o instanceof PEMKeyPair) {
            privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(((PEMKeyPair) o).getPrivateKeyInfo().getEncoded()));
        }
        return genSelfCertificateFromCa(selfSignConfig, caCertificate, privateKey);
    }


    /**
     * CA  Gen Self 证书
     *
     * @param config        配置
     * @param caCertificate CA证书
     * @param caPrivateKey  CA 私钥年
     * @return {@link SelfSignCertificateEntity}
     * @throws Exception 例外
     */
    public static SelfSignCertificateEntity genSelfCertificateFromCa(
            SelfSignConfig config,
            X509Certificate caCertificate,
            PrivateKey caPrivateKey) throws Exception {
        SelfSignConfig.Certificate certificateConfig = config.getCertificate();
        JcaX509CertificateConverter jcaX509CertificateConverter = new JcaX509CertificateConverter().setProvider(BC_PROVIDER);
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(config.getAlgorithm(), BC_PROVIDER);
        keyPairGenerator.initialize(certificateConfig.getInitializeSize());
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.YEAR, certificateConfig.getValidityYear());
        Date endDate = calendar.getTime();
        X509CertificateHolder caCertificateHolder = new X509CertificateHolder(caCertificate.getEncoded());
        //证书相关
        X500Name issuedCertSubject = new X500Name(config.getCertificate().getDn());
        BigInteger issuedCertSerialNum = new BigInteger(Long.toString(new SecureRandom().nextLong()));
        KeyPair issuedCertKeyPair = keyPairGenerator.generateKeyPair();
        PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(issuedCertSubject, issuedCertKeyPair.getPublic());
        JcaContentSignerBuilder csrBuilder = new JcaContentSignerBuilder(certificateConfig.getSignatureAlgorithm()).setProvider(BC_PROVIDER);
        ContentSigner csrContentSigner = csrBuilder.build(caPrivateKey);
        PKCS10CertificationRequest csr = p10Builder.build(csrContentSigner);
        X509v3CertificateBuilder issuedCertBuilder = new X509v3CertificateBuilder(caCertificateHolder.getSubject(), issuedCertSerialNum, startDate, endDate, csr.getSubject(), csr.getSubjectPublicKeyInfo());
        JcaX509ExtensionUtils issuedCertExtUtils = new JcaX509ExtensionUtils();
        issuedCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        issuedCertBuilder.addExtension(Extension.authorityKeyIdentifier, false, issuedCertExtUtils.createAuthorityKeyIdentifier(caCertificate));
        issuedCertBuilder.addExtension(Extension.subjectKeyIdentifier, false, issuedCertExtUtils.createSubjectKeyIdentifier(csr.getSubjectPublicKeyInfo()));
        issuedCertBuilder.addExtension(Extension.keyUsage, false, new KeyUsage(KeyUsage.keyEncipherment | KeyUsage.digitalSignature));
        issuedCertBuilder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(new KeyPurposeId[]{KeyPurposeId.id_kp_clientAuth, KeyPurposeId.id_kp_serverAuth}));
        ASN1Encodable[] asn1Encodables = certificateConfig.getHosts().stream().map(item -> {
            if (IPAddress.isValid(item)) {
                return new GeneralName(GeneralName.iPAddress, item);
            }
            return new GeneralName(GeneralName.dNSName, item);
        }).toArray(ASN1Encodable[]::new);
        //设置信任域名或者ip
        if (asn1Encodables.length > 0) {
            issuedCertBuilder.addExtension(Extension.subjectAlternativeName, false, new DERSequence(asn1Encodables));
        }

        X509CertificateHolder issuedCertHolder = issuedCertBuilder.build(csrContentSigner);
        X509Certificate issuedCert = jcaX509CertificateConverter.getCertificate(issuedCertHolder);
        issuedCert.verify(caCertificate.getPublicKey(), BC_PROVIDER);
        return new SelfSignCertificateEntity(caCertificate, caPrivateKey, issuedCert, issuedCertKeyPair.getPrivate());
    }

    public static SelfSignCertificateEntity genCaCertificate(SelfSignConfig config) throws Exception {
        SelfSignConfig.CA ca = config.getCa();
        JcaX509CertificateConverter jcaX509CertificateConverter = new JcaX509CertificateConverter().setProvider(BC_PROVIDER);
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(config.getAlgorithm(), BC_PROVIDER);
        keyPairGenerator.initialize(ca.getInitializeSize());
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.YEAR, ca.getValidityYear());
        Date endDate = calendar.getTime();
        KeyPair rootKeyPair = keyPairGenerator.generateKeyPair();
        BigInteger rootSerialNum = new BigInteger(Long.toString(new SecureRandom().nextLong()));
        X500Name rootCertIssuer = new X500Name(ca.getDn());
        ContentSigner rootCertContentSigner = new JcaContentSignerBuilder(ca.getSignatureAlgorithm()).setProvider(BC_PROVIDER).build(rootKeyPair.getPrivate());
        X509v3CertificateBuilder rootCertBuilder = new JcaX509v3CertificateBuilder(rootCertIssuer, rootSerialNum, startDate, endDate, rootCertIssuer, rootKeyPair.getPublic());
        JcaX509ExtensionUtils rootCertExtUtils = new JcaX509ExtensionUtils();
        rootCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        rootCertBuilder.addExtension(Extension.subjectKeyIdentifier, false, rootCertExtUtils.createSubjectKeyIdentifier(rootKeyPair.getPublic()));
        X509CertificateHolder rootCertHolder = rootCertBuilder.build(rootCertContentSigner);
        X509Certificate rootCert = jcaX509CertificateConverter.getCertificate(rootCertHolder);
        return new SelfSignCertificateEntity(rootCert, rootKeyPair.getPrivate(), rootCert, rootKeyPair.getPrivate());
    }

    /**
     * 生成自签名证书
     *
     * @param selfSignConfig 自签名配置
     * @return {@link SelfSignCertificateEntity}
     * @throws Exception 例外
     */
    public static SelfSignCertificateEntity genSelfCertificate(SelfSignConfig selfSignConfig) throws Exception {
        SelfSignCertificateEntity ca = genCaCertificate(selfSignConfig);
        return genSelfCertificateFromCa(selfSignConfig, ca.getCa(), ca.getCaKey());
    }
}
