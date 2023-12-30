package org.aswatson.rmj.kafka;

import com.redwood.scheduler.api.model.BusinessKeyLookup;
import com.redwood.scheduler.api.model.Credential;
import com.redwood.scheduler.api.model.Database;
import com.redwood.scheduler.api.model.Document;
import com.redwood.scheduler.api.model.Job;
import com.redwood.scheduler.api.model.JobParameter;
import com.redwood.scheduler.api.model.Partition;
import com.redwood.scheduler.api.model.SchedulerSession;
import com.redwood.scheduler.api.model.Table;
import com.redwood.scheduler.api.model.TableValue;
import com.redwood.scheduler.api.model.enumeration.SimpleConstraintType;
import com.redwood.scheduler.api.scripting.variables.ScriptSessionFactory;
import java.util.HashMap;

import org.asw.kafkafactory.Credentials;
import org.asw.kafkafactory.KafkaClientFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Link between RunMyJobs and KafkClientFactory.<br>
 * JobParameter reads all job parameters and serializes them on an instance of
 * the KafkaClientFactory<br>
 * 
 * dependencies:
 * RunMyJobs
 * Jackson-databind
 * 
 * 
 * 
 * @author JKALMA
 */
public class JobParameterMap {

	private KafkaClientFactory clientFactory;

	/**
	 * return an instance of KafkaClientFactory class
	 * 
	 * @return instance of KafkaClientFactory
	 */
	public KafkaClientFactory getClientFactory() {
		return clientFactory;
	}

	/**
	 * Instantiate class with Job. Job parameters are mapped on an instance of the
	 * KafkaClientFactory. that can be retrieved with the getClentFactory()
	 * 
	 * ProcessDefintion All arameters must be of Type String. ParameterValues that
	 * need to be evaluated from the GLOBAL.KAFKA_CONFIG table, should be configure
	 * accordingly (Simple Constraint Type Table, wit Constraint Data KAFKA_CONFIG)
	 * 
	 * @param j Job - the Job Object.
	 * @throws Exception generic exception
	 */
	public JobParameterMap(Job j) throws Exception {
		HashMap<String, Object> parameterValuesMap = new HashMap<String, Object>();

		for (JobParameter jp : j.getJobParameters()) {
			
			Object object = jp.getCurrentValueString();
			
			if (jp.getJobDefinitionParameter().getSimpleConstraintType().equals((SimpleConstraintType.Table))) {
				// String tableName = jp.getJobDefinitionParameter().getSimpleConstraintData();
				String tableValue = getTableValue(jp.getCurrentValueString());
				switch (tableValue.split(":")[0]) {
				case "Document":
					object = this.getDocumentData(tableValue);
					break;
				case "Credential":
					object = this.getCredentials(tableValue);
					break;
				case "Database":
					object = this.getJdbcUrl(tableValue);
					break;
				default:
					object = tableValue;
				}
			}

			if (jp.getJobDefinitionParameter().getSimpleConstraintType().equals((SimpleConstraintType.QueryFilter))) {
				switch (jp.getJobDefinitionParameter().getSimpleConstraintData()) {
				case "QueryFilter:User.Redwood System.Database.Database%2e;all":
					object = this.getJdbcUrl(jp.getCurrentValueString());
					break;
				case "QueryFilter:User.Redwood System.Credential.Credential%2e;all":
					object = this.getCredentials(jp.getCurrentValueString());
					break;
				case "QueryFilter:User.Redwood System.Credential.Document%2e;all":
					object = this.getDocumentData(jp.getCurrentValueString());
					break;
				default:
					//
				}
			}
			
			parameterValuesMap.put(jp.getJobDefinitionParameter().getName(), object);
		}
		
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		clientFactory = objectMapper.convertValue(parameterValuesMap, KafkaClientFactory.class);
	}

	private String getTableValue(String searchKey) {
		SchedulerSession localSession = ScriptSessionFactory.getSession();
		Partition p = localSession.getPartitionByName("GLOBAL");
		Table t = p.getTableByName("KAFKA_CONFIG");
		TableValue tv = t.getTableValueBySearchKeySearchColumnName(searchKey, "Value");
		return tv.getColumnValue();
	}

	private String getDocumentData(String key) {
		SchedulerSession localSession = ScriptSessionFactory.getSession();
		Document document = BusinessKeyLookup.getDocumentByBusinessKey(localSession, key);
		return document.getDataAsString();
	}

	private Credentials getCredentials(String key) throws Exception {
		SchedulerSession localSession = ScriptSessionFactory.getSession();
		Credential credential = BusinessKeyLookup.getCredentialByBusinessKey(localSession, key);
		return new Credentials(credential.getRealUser(), localSession.unprotectPassword(credential.getProtectedPassword()));
	}

	private String getJdbcUrl(String key) {
		SchedulerSession localSession = ScriptSessionFactory.getSession();
		Database database = BusinessKeyLookup.getDatabaseByBusinessKey(localSession, key);
		return database.getJdbcUrl();
	}

}