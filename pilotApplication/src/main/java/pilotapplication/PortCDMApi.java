package pilotapplication;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import eu.portcdm.amss.client.StateupdateApi;
import eu.portcdm.client.ApiException;
import eu.portcdm.client.service.PortcallsApi;
import eu.portcdm.dto.LocationTimeSequence;
import eu.portcdm.dto.PortCallSummary;
import eu.portcdm.mb.client.MessageQueueServiceApi;
import eu.portcdm.mb.dto.Filter;
import eu.portcdm.messaging.LocationReferenceObject;
import eu.portcdm.messaging.LogicalLocation;
import eu.portcdm.messaging.PortCallMessage;
import eu.portcdm.messaging.TimeType;
import se.viktoria.stm.portcdm.connector.common.SubmissionService;
import se.viktoria.stm.portcdm.connector.common.util.PortCallMessageBuilder;
import se.viktoria.stm.portcdm.connector.common.util.StateWrapper;
import se.viktoria.util.Configuration;

public class PortCDMApi {
	
	// Parameters for the external development machine
	private final String DEV_BASE_URL = "http://dev.portcdm.eu:8080/";
	private final String DEV_USERNAME = "viktoria";
	private final String DEV_PASSWORD = "vik123"; 
	private final String DEV_API_KEY = "pilot";
	private final int DEV_TIMEOUT = 20000;
	private  final String DEV_CONFIG_FILE_NAME = "dev_portcdm.conf";
			
	// Parameters for the local virtual machine
	private final String VM_BASE_URL = "http://192.168.56.101:8080/";
	private final String VM_USERNAME = "porter";
	private final String VM_PASSWORD = "porter"; // LOL security
	private final String VM_API_KEY = "pilot";	
	private final int VM_TIMEOUT = 7000;
	private  final String VM_CONFIG_FILE_NAME = "vm_portcdm.conf";
	
	// Paths to PortCDMs different modules
	private final String MESSAGE_BROKER_PATH = "mb";
	private final String PORT_CDM_SERVICES_PATH = "dmp";
	private final String PORT_CDM_AMSS_PATH = "amss";
		
	public SubmissionService submissionService;
	public MessageQueueServiceApi messageBrokerAPI;
	public PortcallsApi portCallsAPI;
	public StateupdateApi AMSSApi; 
	
	/**
	 * Constructor for the PortCDMApi object
	 * 
	 * @param connectToExternalServer connect to external dev server if <code>true</code>, else connect to local vm
	 */
	public PortCDMApi(boolean connectToExternalServer) {
		if (connectToExternalServer) {
			initSubmissionService(DEV_CONFIG_FILE_NAME, null);
			initMessageBrokerAPI(DEV_BASE_URL + MESSAGE_BROKER_PATH, DEV_TIMEOUT, DEV_USERNAME, DEV_PASSWORD, DEV_API_KEY);
			initPortCallsAPI(DEV_BASE_URL + PORT_CDM_SERVICES_PATH, DEV_TIMEOUT, DEV_USERNAME, DEV_PASSWORD, DEV_API_KEY);
			initAMSSApi(DEV_BASE_URL + PORT_CDM_SERVICES_PATH, DEV_TIMEOUT, DEV_USERNAME, DEV_PASSWORD, DEV_API_KEY);
		}
		else {
			initSubmissionService(VM_CONFIG_FILE_NAME, null);
			initMessageBrokerAPI(VM_BASE_URL + MESSAGE_BROKER_PATH, VM_TIMEOUT, VM_USERNAME, VM_PASSWORD, VM_API_KEY);
			initPortCallsAPI(VM_BASE_URL + PORT_CDM_SERVICES_PATH, VM_TIMEOUT, VM_USERNAME, VM_PASSWORD, VM_API_KEY);
			initAMSSApi(VM_BASE_URL + PORT_CDM_AMSS_PATH, VM_TIMEOUT, VM_USERNAME, VM_PASSWORD, VM_API_KEY); 
		}	
	}
	
	/**
	 * Init connector (submissionService) to portCDM.
	 * 
	 * @param configFileName	filename for application configuration	
	 * @param configFileDir		leaf directory for application configuration
	 */
	private void initSubmissionService(String configFileName, String configFileDir) {
		Configuration config = new Configuration(
				configFileName, 
				configFileDir,
				new Predicate<Map.Entry<Object, Object>>() {
					@Override
					public boolean test(Map.Entry<Object, Object> objectObjectEntry) {
						return !objectObjectEntry.getKey().toString().equals("pass");
				}	
		});
		config.reload();		
		
		submissionService = new SubmissionService();
		submissionService.addConnectors(config);
	}
	
	/**
	 * Init API to portCDM message broker.
	 */
	private void initMessageBrokerAPI(String baseUrl, int timeout, String username, String password, String apikey) {
		eu.portcdm.mb.client.ApiClient connectorClient = new eu.portcdm.mb.client.ApiClient();
		connectorClient.setBasePath(baseUrl);
		connectorClient.setConnectTimeout(timeout);
		connectorClient.addDefaultHeader("X-PortCDM-UserId", username);
        connectorClient.addDefaultHeader("X-PortCDM-Password", password);
        connectorClient.addDefaultHeader("X-PortCDM-APIKey", apikey);
		messageBrokerAPI = new MessageQueueServiceApi(connectorClient);
	}
	
	/**
	 * Init API to portCDM port call manager.
	 */
	private void initPortCallsAPI(String baseUrl, int timeout, String username, String password, String apikey) {
		eu.portcdm.client.ApiClient connectorClient = new eu.portcdm.client.ApiClient();
		connectorClient.setBasePath(baseUrl);
		connectorClient.setConnectTimeout(timeout);
		connectorClient.addDefaultHeader("X-PortCDM-UserId", username);
        connectorClient.addDefaultHeader("X-PortCDM-Password", password);
        connectorClient.addDefaultHeader("X-PortCDM-APIKey", apikey);
		portCallsAPI = new PortcallsApi(connectorClient);				
	}
	
	/**
     *  Init API to portCDM assisted message submission service (AMSS)
     */
    private void initAMSSApi(String baseUrl, int timeout, String username, String password, String apikey) {
        eu.portcdm.amss.client.ApiClient connectorClient = new eu.portcdm.amss.client.ApiClient();
        connectorClient.setBasePath(baseUrl);    
        connectorClient.addDefaultHeader("X-PortCDM-UserId", username);
        connectorClient.addDefaultHeader("X-PortCDM-Password", password);
        connectorClient.addDefaultHeader("X-PortCDM-APIKey", apikey);
        AMSSApi = new StateupdateApi(connectorClient);
    }
	
	/**
	 * Get summary of portcalls from portCDM.
	 * 
	 * @param max maximum number of portcalls to fetch
	 * @return list of portcall summaries
	 */
	public List<PortCallSummary> getPortCalls(int max) {
		List<PortCallSummary> portcalls = null;
		try {
			portcalls = portCallsAPI.getAllPortCalls(max);
		} catch (ApiException e) {
			System.out.println("Couldn't fetch portcalls.");
			System.out.println(e.getCode() + " " + e.getMessage());
			System.out.println(e.getResponseBody());
		}
		return portcalls;
	}
	
	/**
	 * Submit a portcall message to PortCDM. 
	 * 
	 * @param pcm portcall messages to be sent
	 */
	public void sendPortCallMessage(PortCallMessage pcm) {
		try {
            AMSSApi.sendMessage(pcm);
        } 
        catch (eu.portcdm.amss.client.ApiException e) {
        	System.out.println("Couldn't send message.");
        	System.out.println(e.getResponseHeaders() + " " + e.getMessage() + '\n' + e.getResponseBody());
        }
	}
	
	/**
	 * Post a queue with the given filters to portCDM.
	 * 
	 * @param filters list of filters that 
	 * @return id of the newly created queue
	 */
	public String postQueue(List<Filter> filters) {
		String queueId = null;
		try {
			queueId = messageBrokerAPI.mqsPost(filters);
		} 
		catch (eu.portcdm.mb.client.ApiException e) {
			System.out.println("Couldn't post queue");
			System.out.println(e.getResponseHeaders() + " " + e.getMessage() + '\n' + e.getResponseBody());
		}
		return queueId;
	}
	
	/**
	 * Fetch all messages from the queue with the given id.
	 * 
	 * @param queueId id of the queue of interest
	 * @return all messages fetched from the queue
	 */
	public List<PortCallMessage> fetchMessagesFromQueue(String queueId) {
		List<PortCallMessage> messages = null;
		try {
			messages = messageBrokerAPI.mqsQueueGet(queueId);
		} 
		catch (eu.portcdm.mb.client.ApiException e) {
			System.out.println("Couldn't fetch messages.");
			System.out.println(e.getResponseHeaders() + " " + e.getMessage() + '\n' + e.getResponseBody());
		} 
		return messages;
	}
	
	/**
	 * Returns a portcall message that can be used for testing.
	 * 
	 * @return bogus portcall message
	 */
    public PortCallMessage getExampleMessage() {
        StateWrapper stateWrapper = new StateWrapper(
                LocationReferenceObject.VESSEL, //referenceObject
                LocationTimeSequence.ARRIVAL_TO, //ARRIVAL_TO or DEPARTURE_FROM
                LogicalLocation.BERTH, //Type of required location
                53.50, //Latitude of required location
                53.50, //Longitude of required location
                "Skarvik Harbour 518", //Name of required location
                LogicalLocation.ANCHORING_AREA, //Type of optional location
                52.50, //Latitude of optional location
                52.50, //Longitude of optional location
                "Dana Fjord D1");//Name of optional location
        
        //Change dates from 2017-03-23 06:40:00 to 2017-03-23T06:40:00Z 
        PortCallMessage portCallMessage = PortCallMessageBuilder.build(
        		"urn:x-mrn:stm:portcdm:local_port_call:SEGOT:DHC:52723", //localPortCallId
        		"urn:x-mrn:stm:portcdm:local_job:FENIX_SMA:990198125", //localJobId
                stateWrapper, //StateWrapper created above
                "2017-03-23T06:45:00Z", //Message's time
                TimeType.ESTIMATED, //Message's timeType
                "urn:x-mrn:stm:vessel:IMO:9259501", //vesselId
                "2017-03-23T06:38:51Z", //reportedAt (optional)
                "Pilot", //reportedBy (optional)
                "urn:mrn:stm:portcdm:message:5eadbb1c-6be7-4cf2-bd6d-f0af5a0c35dc", //groupWith (optional), messageId of the message to group with.
                "this is a test" //comment (optional)
        );

        return portCallMessage;
    }
}