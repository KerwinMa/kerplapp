
package net.binaryparadox.kerplapp;

import org.spongycastle.asn1.ASN1EncodableVector;
import org.spongycastle.asn1.ASN1Sequence;
import org.spongycastle.asn1.DEROctetString;
import org.spongycastle.asn1.DERSequence;
import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.asn1.x509.GeneralName;
import org.spongycastle.asn1.x509.GeneralNames;
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo;
import org.spongycastle.asn1.x509.X509Extension;
import org.spongycastle.asn1.x509.X509Extensions;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.X509v1CertificateBuilder;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.OperatorCreationException;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;

import javax.net.ssl.KeyManagerFactory;

import kellinwood.security.zipsigner.ZipSigner;

public class KerplappKeyStore {
    //TODO: Address exception handling in a uniform way across the KeyStore & application

    static {
        Security.insertProviderAt(
                new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    public static final String INDEX_CERT_ALIAS = "fdroid";
    public static final String HTTP_CERT_ALIAS = "https";

    private static final String DEFAULT_SIG_ALG = "SHA1withRSA";
    private static final String DEFAULT_KEY_ALGO = "RSA";
    private static final int DEFAULT_KEY_BITS = 2048;

    private KeyStore keyStore;
    private KeyManagerFactory keyManagerFactory;
    private File backingFile;

    public KerplappKeyStore(File backingFile) throws KeyStoreException, NoSuchAlgorithmException,
            CertificateException, IOException, OperatorCreationException, UnrecoverableKeyException {
        this.backingFile = backingFile;
        this.keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        keyManagerFactory = KeyManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm());

        // If there isn't a persisted BKS keystore on disk we need to
        // create a new empty keystore
        if (!backingFile.exists())
        {
            // Init a new keystore with a blank passphrase
            keyStore.load(null, "".toCharArray());

            // Generate a random key pair to associate with the INDEX_CERT_ALIAS
            // certificate in the keystore. This keypair will be used for the
            // HTTPS cert
            // as well.
            KeyPair rndKeys = generateRandomKeypair();

            // Generate a self signed certificate for signing the index.jar
            // We can't generate the HTTPS certificate until we know what the IP
            // address will be to use for the CN field.
            X500Name subject = new X500Name("O=Kerplapp,OU=GuardianProject");
            Certificate indexCert = generateSelfSignedCertChain(rndKeys, subject);

            addToStore(INDEX_CERT_ALIAS, rndKeys, indexCert);
        } else {
            keyStore.load(new FileInputStream(backingFile), "".toCharArray());
            keyManagerFactory.init(keyStore, "".toCharArray());
        }
    }

    public void setupHTTPSCertificate(String hostname) throws CertificateException,
            OperatorCreationException, KeyStoreException, NoSuchAlgorithmException,
            FileNotFoundException, IOException, UnrecoverableKeyException
    {
        // Get the existing private/public keypair to use for the HTTPS cert
        KeyPair kerplappKeypair = getKerplappKeypair();

        // Once we have a hostname we can generate a self signed cert with a
        // valid CN field to stash into the keystore in a predictable place.
        // If the hostname changes we should run this method again to stomp
        // old HTTPS_CERT_ALIAS entries.
        X500Name subject = new X500Name("CN=" + hostname);

        Certificate indexCert = generateSelfSignedCertChain(kerplappKeypair, subject, hostname);

        addToStore(HTTP_CERT_ALIAS, kerplappKeypair, indexCert);
    }

    public File getKeyStoreFile() {
        return backingFile;
    }

    public KeyStore getKeyStore()
    {
        return keyStore;
    }

    public KeyManagerFactory getKeyManagerFactory()
    {
        return keyManagerFactory;
    }

    public void signZip(File input, File output)
    {
        try {
            ZipSigner zipSigner = new ZipSigner();

            KeyStore keystore = getKeyStore();
            X509Certificate cert = (X509Certificate) keystore.getCertificate(INDEX_CERT_ALIAS);

            KeyPair kp = getKerplappKeypair();
            PrivateKey priv = kp.getPrivate();

            zipSigner.setKeys("kerplapp", cert, priv, DEFAULT_SIG_ALG, null);
            zipSigner.signZip(input.getAbsolutePath(), output.getAbsolutePath());

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    private KeyPair getKerplappKeypair() throws KeyStoreException, UnrecoverableKeyException,
            NoSuchAlgorithmException
    {
        // You can't store a keypair without an associated certificate chain so,
        // we'll use the INDEX_CERT_ALIAS as the de-facto keypair/certificate
        // chain. This cert/key is initialized when the KerplappKeyStore is
        // constructed for the first time and should *always* be present.
        Key key = keyStore.getKey(INDEX_CERT_ALIAS, "".toCharArray());

        if (key instanceof PrivateKey) {
            Certificate cert = keyStore.getCertificate(INDEX_CERT_ALIAS);
            PublicKey publicKey = cert.getPublicKey();
            return new KeyPair(publicKey, (PrivateKey) key);
        }

        return null;
    }

    // This is take from FDroid: org.fdroid.fdroid.DB.calcFingerprint()
    // TODO once this code is part of FDroid, replace this with DB.calcFingerprint()
    public String getFingerprint() {
        String ret = null;
        try {
            Key key = keyStore.getKey(INDEX_CERT_ALIAS, "".toCharArray());
            if (key instanceof PrivateKey) {
                Certificate cert = keyStore.getCertificate(INDEX_CERT_ALIAS);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(cert.getEncoded());
                byte[] fingerprint = digest.digest();
                Formatter formatter = new Formatter(new StringBuilder());
                for (int i = 1; i < fingerprint.length; i++) {
                    formatter.format("%02X", fingerprint[i]);
                }
                ret = formatter.toString();
                formatter.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ret;
    }

    private void addToStore(String alias, KeyPair kp, Certificate cert) throws KeyStoreException,
            NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException,
            UnrecoverableKeyException
    {
        Certificate[] chain = new Certificate[] {
                cert
        };
        keyStore.setKeyEntry(alias, kp.getPrivate(),
                "".toCharArray(), chain);

        keyStore.store(new FileOutputStream(backingFile), "".toCharArray());
        keyManagerFactory.init(keyStore, "".toCharArray());
    }

    private KeyPair generateRandomKeypair() throws NoSuchAlgorithmException
    {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(DEFAULT_KEY_ALGO);
        keyPairGenerator.initialize(DEFAULT_KEY_BITS);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return keyPair;
    }
    
    private Certificate generateSelfSignedCertChain(KeyPair kp, X500Name subject)
            throws CertificateException, OperatorCreationException, IOException
    {
    	return generateSelfSignedCertChain(kp, subject, null);
    }

    private Certificate generateSelfSignedCertChain(KeyPair kp, X500Name subject, String hostname)
            throws CertificateException, OperatorCreationException, IOException
    {
        SecureRandom rand = new SecureRandom();
        PrivateKey privKey = kp.getPrivate();
        PublicKey pubKey = kp.getPublic();
        ContentSigner sigGen = new JcaContentSignerBuilder(DEFAULT_SIG_ALG).build(privKey);

        SubjectPublicKeyInfo subPubKeyInfo = new SubjectPublicKeyInfo(
                ASN1Sequence.getInstance(pubKey.getEncoded()));

        Date startDate = new Date(); // now

        Calendar c = Calendar.getInstance();
        c.setTime(startDate);
        c.add(Calendar.YEAR, 1);
        Date endDate = c.getTime();
        
        X509v3CertificateBuilder v3CertGen = new X509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(rand.nextLong()),
                startDate, endDate,
                subject,
                subPubKeyInfo);
        
        if(hostname != null)
        {
        	
	        GeneralNames subjectAltName = new GeneralNames(
	                new GeneralName(GeneralName.iPAddress, hostname));
	
	        //X509Extension extension = new X509Extension(false, new DEROctetString(subjectAltName));
	
	        v3CertGen.addExtension(X509Extension.subjectAlternativeName, false, subjectAltName);
        }
        
        X509CertificateHolder certHolder = v3CertGen.build(sigGen);
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

}
