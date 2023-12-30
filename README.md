Map the parameters of a submitted Redwood RunMyJobs processDefinition on an instance of the KafkaClientFactory for processing of Kafka Consumers and Producers

Dependencies:
- [KafkaClientFactory](https://github.com/JohannesKalma/KafkaClientFactory)
- [Redwood RunMyJobs](https://redwood.com)

Add ParameterMapper class to your library, with all depencies.

Create a jobDefinition with any of these (case sensitive) parameters
DTO fields that can be used for mapping:
- bootstrapServers String (list of brokers)
- bootstrapServersCredentials Credentials
- bootstrapServerTruststoreCertificate String (the PEM Certificate Document)
- schemaRegistrycredentials Credentials
- schemaRegistryURL String
- typeDeSer KafkaClientFactory.typeDeSer - groupId String
- topic String
- className String (DTO class needed for AVRO (de)serializing)
- key String
- value String
- partition String
- offset String
- jdbcUrl String
- jdbcCredentials Credentials
- jdbcQuery String

Values of these parameter might be of a (key,value) table or
a database,credential or document businessKey.
This can be configured with a simple constrainttype on a jobDefinition.
Both Table and Query Filter can be used.
Supported Queryfilters are Database (jdbc), Credential (protocol: login, Externaly available) or Document (for the Pem Cert)
When table values are used, then the businesskey of Database, Credential or Document are expected.

Content of the jobDefintionsource can be then:
```
{
  /*publish a message (single or from query, depends on DTO settings*/
  new Producer(new JobParameterMap(jcsJob).getClientFactory().setPrintwriter(jcsOut)).publish().printMetadata();
}
```
or
```
{
  /*seek a message*/
  JobParameterMap pm = new JobParameterMap(jcsJob);
  KafkaClientFactory cf = pm.getClientFactory();
  cf.setPrintwriter(jcsOut)

  Consumer c = new Consumer();
  c.printValues();
  c.seek();
}
```
or with chained methods:
```
{
  /*seek a message*/
  new Consumer(new JobParameterMap(jcsJob).getClientFactory().setPrintwriter(jcsOut)).printValues().seek();
}
```
