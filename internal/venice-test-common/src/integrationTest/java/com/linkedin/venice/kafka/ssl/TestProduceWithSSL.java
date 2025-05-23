package com.linkedin.venice.kafka.ssl;

import static com.linkedin.venice.utils.IntegrationTestPushUtils.createStoreForJob;
import static com.linkedin.venice.utils.IntegrationTestPushUtils.sslVPJProps;
import static com.linkedin.venice.utils.TestWriteUtils.getTempDataDirectory;
import static com.linkedin.venice.vpj.VenicePushJobConstants.ENABLE_SSL;
import static com.linkedin.venice.vpj.VenicePushJobConstants.KAFKA_INPUT_BROKER_URL;
import static com.linkedin.venice.vpj.VenicePushJobConstants.SOURCE_KAFKA;
import static com.linkedin.venice.vpj.VenicePushJobConstants.SSL_KEY_PASSWORD_PROPERTY_NAME;
import static com.linkedin.venice.vpj.VenicePushJobConstants.SSL_KEY_STORE_PASSWORD_PROPERTY_NAME;
import static com.linkedin.venice.vpj.VenicePushJobConstants.SSL_KEY_STORE_PROPERTY_NAME;
import static com.linkedin.venice.vpj.VenicePushJobConstants.SSL_TRUST_STORE_PROPERTY_NAME;

import com.linkedin.venice.client.store.AvroGenericStoreClient;
import com.linkedin.venice.client.store.ClientConfig;
import com.linkedin.venice.client.store.ClientFactory;
import com.linkedin.venice.compression.CompressionStrategy;
import com.linkedin.venice.controllerapi.ControllerClient;
import com.linkedin.venice.controllerapi.ControllerResponse;
import com.linkedin.venice.controllerapi.UpdateStoreQueryParams;
import com.linkedin.venice.controllerapi.VersionCreationResponse;
import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.hadoop.input.kafka.KafkaInputRecordReader;
import com.linkedin.venice.integration.utils.ServiceFactory;
import com.linkedin.venice.integration.utils.VeniceClusterCreateOptions;
import com.linkedin.venice.integration.utils.VeniceClusterWrapper;
import com.linkedin.venice.utils.IntegrationTestPushUtils;
import com.linkedin.venice.utils.SslUtils;
import com.linkedin.venice.utils.TestUtils;
import com.linkedin.venice.utils.TestWriteUtils;
import com.linkedin.venice.utils.Time;
import com.linkedin.venice.utils.Utils;
import com.linkedin.venice.writer.VeniceWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.avro.Schema;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class TestProduceWithSSL {
  private VeniceClusterWrapper cluster;

  @BeforeClass
  public void setUp() {
    Utils.thisIsLocalhost();
    VeniceClusterCreateOptions options = new VeniceClusterCreateOptions.Builder().sslToKafka(true).build();
    cluster = ServiceFactory.getVeniceCluster(options);
  }

  @AfterClass
  public void cleanUp() {
    cluster.close();
  }

  @Test(timeOut = 60 * Time.MS_PER_SECOND)
  public void testVeniceWriterSupportSSL() throws ExecutionException, InterruptedException {
    String storeName = Utils.getUniqueString("testVeniceWriterSupportSSL");
    cluster.getNewStore(storeName);
    VersionCreationResponse response = cluster.getNewVersion(storeName);
    Assert.assertFalse(response.isError());
    int version = response.getVersion();
    String topic = response.getKafkaTopic();
    VeniceWriter<String, String, byte[]> writer = cluster.getSslVeniceWriter(topic);
    String testKey = "key";
    String testVal = "value";
    writer.broadcastStartOfPush(new HashMap<>());
    writer.put(testKey, testVal, 1);
    writer.broadcastEndOfPush(new HashMap<>());

    // Wait for storage node to finish consuming, and new version to be activated
    String controllerUrl = cluster.getAllControllersURLs();
    ControllerClient controllerClient = new ControllerClient(cluster.getClusterName(), controllerUrl);
    TestUtils.waitForNonDeterministicCompletion(30, TimeUnit.SECONDS, () -> {
      int currentVersion = controllerClient.getStore(storeName).getStore().getCurrentVersion();
      return currentVersion == version;
    });

    AvroGenericStoreClient<String, CharSequence> storeClient = ClientFactory.getAndStartGenericAvroClient(
        ClientConfig.defaultGenericClientConfig(storeName)
            .setVeniceURL(cluster.getRandomRouterURL())
            .setSslFactory(SslUtils.getVeniceLocalSslFactory()));

    Assert.assertEquals(storeClient.get(testKey).get().toString(), testVal);
    writer.close();
    controllerClient.close();
  }

  private byte[] readFile(String path) throws IOException {
    File file = new File(path);
    try (FileInputStream fis = new FileInputStream(file)) {
      byte[] data = new byte[(int) file.length()];
      int length = fis.read(data);
      Assert.assertEquals(length, file.length());
      return data;
    }
  }

  @Test(timeOut = 90 * Time.MS_PER_SECOND)
  public void testVenicePushJobSupportSSL() throws Exception {
    VeniceClusterWrapper cluster = this.cluster;
    File inputDir = getTempDataDirectory();
    String storeName = Utils.getUniqueString("store");
    Schema recordSchema = TestWriteUtils.writeSimpleAvroFileWithStringToStringSchema(inputDir);
    String inputDirPath = "file://" + inputDir.getAbsolutePath();
    Properties props = sslVPJProps(cluster, inputDirPath, storeName);

    String keyStorePropertyName = "ssl.identity";
    String trustStorePropertyName = "ssl.truststore";
    String keyStorePwdPropertyName = "ssl.identity.keystore.password";
    String keyPwdPropertyName = "ssl.identity.key.password";

    props.setProperty(ENABLE_SSL, Boolean.TRUE.toString());
    props.setProperty(SSL_KEY_STORE_PROPERTY_NAME, keyStorePropertyName);
    props.setProperty(SSL_TRUST_STORE_PROPERTY_NAME, trustStorePropertyName);
    props.setProperty(SSL_KEY_STORE_PASSWORD_PROPERTY_NAME, keyStorePwdPropertyName);
    props.setProperty(SSL_KEY_PASSWORD_PROPERTY_NAME, keyPwdPropertyName);
    props.setProperty(
        KafkaInputRecordReader.KIF_RECORD_READER_KAFKA_CONFIG_PREFIX + "send.buffer.bytes",
        Integer.toString(4 * 1024 * 1024));

    // put cert into hadoop user credentials.
    SslUtils.VeniceTlsConfiguration tlsConfiguration = SslUtils.getTlsConfiguration();
    byte[] keyStoreCert = readFile(tlsConfiguration.getKeyStorePath());
    byte[] trustStoreCert = readFile(tlsConfiguration.getTrustStorePath());
    Credentials credentials = new Credentials();
    credentials.addSecretKey(new Text(keyStorePropertyName), keyStoreCert);
    credentials.addSecretKey(new Text(trustStorePropertyName), trustStoreCert);
    credentials.addSecretKey(
        new Text(keyStorePwdPropertyName),
        tlsConfiguration.getKeyStorePassword().getBytes(StandardCharsets.UTF_8));
    credentials.addSecretKey(
        new Text(keyPwdPropertyName),
        tlsConfiguration.getKeyPassphrase().getBytes(StandardCharsets.UTF_8));
    UserGroupInformation.getCurrentUser().addCredentials(credentials);
    // Setup token file
    String filePath = getTempDataDirectory().getAbsolutePath() + "/testHadoopToken";
    credentials.writeTokenStorageFile(new Path(filePath), new Configuration());
    System.setProperty(UserGroupInformation.HADOOP_TOKEN_FILE_LOCATION, filePath);

    Assert.assertEquals(System.getProperty(UserGroupInformation.HADOOP_TOKEN_FILE_LOCATION), filePath);

    createStoreForJob(cluster.getClusterName(), recordSchema, props).close();
    String controllerUrl = cluster.getAllControllersURLs();
    ControllerClient controllerClient = new ControllerClient(cluster.getClusterName(), controllerUrl);
    Assert.assertEquals(
        controllerClient.getStore(storeName).getStore().getCurrentVersion(),
        0,
        "Push has not been start, current should be 0");

    // First push to verify regular push job works fine
    IntegrationTestPushUtils.runVPJ(props);
    TestUtils.waitForNonDeterministicCompletion(30, TimeUnit.SECONDS, () -> {
      int currentVersion = controllerClient.getStore(storeName).getStore().getCurrentVersion();
      return currentVersion == 1;
    });

    // Re-push with Kafka Input Format
    props.setProperty(SOURCE_KAFKA, "true");
    props.setProperty(KAFKA_INPUT_BROKER_URL, cluster.getPubSubBrokerWrapper().getSSLAddress());
    IntegrationTestPushUtils.runVPJ(props);
    TestUtils.waitForNonDeterministicCompletion(30, TimeUnit.SECONDS, () -> {
      int currentVersion = controllerClient.getStore(storeName).getStore().getCurrentVersion();
      return currentVersion == 2;
    });

    // Enable dictionary compression and do a regular push
    ControllerResponse response = controllerClient.updateStore(
        storeName,
        new UpdateStoreQueryParams().setCompressionStrategy(CompressionStrategy.ZSTD_WITH_DICT));
    if (response.isError()) {
      throw new VeniceException(response.getError());
    }
    props.setProperty(SOURCE_KAFKA, "false");
    IntegrationTestPushUtils.runVPJ(props);
    TestUtils.waitForNonDeterministicCompletion(30, TimeUnit.SECONDS, () -> {
      int currentVersion = controllerClient.getStore(storeName).getStore().getCurrentVersion();
      return currentVersion == 3;
    });

    // Re-push with Kafka Input Format and dictionary compression enabled
    props.setProperty(SOURCE_KAFKA, "true");
    IntegrationTestPushUtils.runVPJ(props);
    TestUtils.waitForNonDeterministicCompletion(30, TimeUnit.SECONDS, () -> {
      int currentVersion = controllerClient.getStore(storeName).getStore().getCurrentVersion();
      return currentVersion == 4;
    });
  }
}
