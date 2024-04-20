package org.aswatson.rmj.kafka;

import com.redwood.scheduler.api.model.BusinessKeyLookup;
import com.redwood.scheduler.api.model.Credential;
import com.redwood.scheduler.api.model.Job;
import com.redwood.scheduler.api.model.JobParameter;
import com.redwood.scheduler.api.model.Partition;
import com.redwood.scheduler.api.model.SchedulerSession;
import com.redwood.scheduler.api.model.Table;
import com.redwood.scheduler.api.model.TableValue;
import com.redwood.scheduler.api.model.enumeration.SimpleConstraintType;
import com.redwood.scheduler.api.model.interfaces.RWIterable;
import com.redwood.scheduler.api.scripting.variables.ScriptSessionFactory;

import java.util.Arrays;
import java.util.HashMap;

import org.asw.kafkafactory.Credentials;
import org.asw.kafkafactory.KafkaClientFactory;
import org.asw.kafkafactory.KafkaUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Link between RunMyJobs and KafkClientFactory.<br>
 * JobParameter reads all job parameters and serializes them on an instance of
 * the KafkaClientFactory<br>
 * 
 * dependencies:<br>
 * - RunMyJobs<br>
 * - Jackson-databind<br>
 * - KafkaClientFactory<br>
 * 
 * @author JKALMA
 */
public class JobParameterMap {

	SchedulerSession localSession;

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
	 * <p>
	 * Instantiate class with Job. Job parameters are mapped on an instance of the
	 * KafkaClientFactory. that can be retrieved with the getClentFactory()
	 * </p>
	 * 
	 * ProcessDefintion usage:<br>
	 * All parameters must be any of type String.<br>
	 * <ul>
	 * <li> Bare values do not need a constraint.</li>
	 * <li> ParameterValues can be evaluated from a Table Simple constraint. Table should be configured with Key and Value<br>
	 *   <ul><li>Table can contain a bare value or a RMJ object as Businesskey String (Starting with "Document","Credential" or "Database")</li></ul>
	 * </li>  
	 * <li>ParameterValues can be evaluated from a QueryFilters: Document, Credential, Database.</li>
	 * <li>Credentials parameters "bootstrapServersCredentials", "schemaRegistryCredentials", "jdbcCredentials", when not Table or Query based, must have json string format {"userName":"[name]","password":"[password]"} </li> 
	 * </ul>
	 * 
	 * 
	 * @param j Job - the Job Object.
	 * @throws Exception generic exception
	 */
	public JobParameterMap(Job j) throws Exception {
		this.localSession = ScriptSessionFactory.getSession();
		HashMap<String, Object> parameterValuesMap = new HashMap<String, Object>();

		for (JobParameter jp : j.getJobParameters()) {

			Object object = jp.getCurrentValueString();

			if (jp.getJobDefinitionParameter().getSimpleConstraintType().equals((SimpleConstraintType.Table))) {
				switch (jp.getJobDefinitionParameter().getName()) {
				case "bootstrapConf":
					for (TableValue tv: this.getBootstrapServerConfigTableValues(jp.getJobDefinitionParameter().getSimpleConstraintData(), jp.getCurrentValueString())) {
						switch (tv.getColumnName()) {
						case "bootstrapServers":
							parameterValuesMap.put(tv.getColumnName(), tv.getColumnValue());
							break;
						case "bootstrapServerTruststoreCertificate":
							if (KafkaUtil.isNotBlank(tv.getColumnValue())) {
							  parameterValuesMap.put(tv.getColumnName(), this.getDocumentData(tv.getColumnValue()));
							}
							break;
						case "bootstrapServersCredentials":
							if (KafkaUtil.isNotBlank(tv.getColumnValue())) {
							  parameterValuesMap.put(tv.getColumnName(), this.getCredentials(tv.getColumnValue()));
							}
							break;
						case "schemaRegistryURL":
							if (KafkaUtil.isNotBlank(tv.getColumnValue())) {
							  parameterValuesMap.put(tv.getColumnName(), tv.getColumnValue());
							}
							break;
						case "schemaRegistryCredentials":
							if (KafkaUtil.isNotBlank(tv.getColumnValue())) {
							  parameterValuesMap.put(tv.getColumnName(), this.getCredentials(tv.getColumnValue()));
							}
							break;
						default:
							//
						}					  	
					}
					break;
				case "jdbcConf":
					for (TableValue tv: this.getBootstrapServerConfigTableValues(jp.getJobDefinitionParameter().getSimpleConstraintData(), jp.getCurrentValueString())) {
						switch (tv.getColumnName()) {
						case "jdbcUrl":
							if (KafkaUtil.isNotBlank(tv.getColumnValue())) {
							  parameterValuesMap.put(tv.getColumnName(), tv.getColumnValue());
							}
						break;
						case "jdbcCredentials":
							if (KafkaUtil.isNotBlank(tv.getColumnValue())) {
							  parameterValuesMap.put(tv.getColumnName(), this.getCredentials(tv.getColumnValue()));
							}
							break;
						default:
							//
						}
					}
				  break;
				default:
					String tableValue = getTableValue(jp.getJobDefinitionParameter().getSimpleConstraintData(),
							jp.getCurrentValueString());
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
			}

			if (jp.getJobDefinitionParameter().getSimpleConstraintType().equals((SimpleConstraintType.QueryFilter))) {
				switch (jp.getJobDefinitionParameter().getSimpleConstraintData()) {
				case "QueryFilter:User.Redwood System.Document.Document%2e;all":
					object = this.getDocumentData(jp.getCurrentValueString());
					break;
				case "QueryFilter:User.Redwood System.Credential.Credential%2e;all":
					object = this.getCredentials(jp.getCurrentValueString());
					break;
				case "QueryFilter:User.Redwood System.Database.Database%2e;all":
					object = this.getJdbcUrl(jp.getCurrentValueString());
					break;
				default:
					//
				}
			}
			
			String[] credentialParametersArray = {"bootstrapServersCredentials", "schemaRegistryCredentials", "jdbcCredentials"};
			boolean isCredentialParameter = Arrays.stream(credentialParametersArray).anyMatch(s -> s.equals(jp.getJobDefinitionParameter().getName()));
      if (isCredentialParameter && !(jp.getJobDefinitionParameter().getSimpleConstraintType().equals((SimpleConstraintType.QueryFilter)) || jp.getJobDefinitionParameter().getSimpleConstraintType().equals((SimpleConstraintType.Table)))) {
      	ObjectMapper objectMapper = new ObjectMapper();
    		object = objectMapper.readValue(jp.getCurrentValueString(), Credentials.class);
      }

      parameterValuesMap.put(jp.getJobDefinitionParameter().getName(), object);
		}

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		clientFactory = objectMapper.convertValue(parameterValuesMap, KafkaClientFactory.class);
	}
	
	private Table getTable(String tb) {
		Partition p = localSession.getDefaultPartition();
		String t = tb;
		String[] ts = t.split("\\.");

		if (ts.length == 2) {
			p = localSession.getPartitionByName(ts[0]);
			t = ts[1];
		}

		return localSession.getTableByName(p, t);		
	}
	
	private RWIterable<TableValue> getBootstrapServerConfigTableValues(String table, String key) {
		Table t = this.getTable(table);
	  return t.getTableRowByKey(key);
	}
	
	private String getTableValue(String tb, String searchKey) {
		/*
		Partition p = localSession.getDefaultPartition();
		String t = tb;
		String[] ts = t.split("\\.");

		if (ts.length == 2) {
			p = localSession.getPartitionByName(ts[0]);
			t = ts[1];
		}
    */
		Table table = this.getTable(tb);
		TableValue tv = table.getTableValueBySearchKeySearchColumnName(searchKey, "Value");
		return tv.getColumnValue();
	}

	private String getDocumentData(String key) {
		return BusinessKeyLookup.getDocumentByBusinessKey(localSession, key).getDataAsString();
	}

	private Credentials getCredentials(String key) throws Exception {
		Credential credential = BusinessKeyLookup.getCredentialByBusinessKey(localSession, key);
		return new Credentials(credential.getRealUser(), localSession.unprotectPassword(credential.getProtectedPassword()));
	}

	private String getJdbcUrl(String key) {
		return BusinessKeyLookup.getDatabaseByBusinessKey(localSession, key).getJdbcUrl();
	}

}