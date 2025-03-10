package beamline.sources;

import java.util.UUID;

import org.deckfour.xes.model.XTrace;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import beamline.exceptions.SourceException;
import beamline.utils.EventUtils;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * This implementation of a {@link XesSource} produces events as they are
 * observed in an MQTT-XES broker. This source produces a hot observable.
 * 
 * <p>
 * Example of usage:
 * <pre>
 * XesSource source = new MQTTXesSource("tcp://broker.hivemq.com:1883", "topicBase", "processName");
 * source.prepare();
 * </pre>
 * 
 * <p>
 * See also the documentation of MQTT-XES at http://www.beamline.cloud/mqtt-xes/
 * 
 * @author Andrea Burattin
 */
public class MQTTXesSource implements XesSource {

	private String processName;
	private String brokerHost;
	private String topicBase;
	private PublishSubject<XTrace> ps;
	
	/**
	 * Constructs the source
	 * 
	 * @param brokerHost the URL of the broker host
	 * @param topicBase the base of the topic for the
	 * @param processName the name of the process
	 */
	public MQTTXesSource(String brokerHost, String topicBase, String processName) {
		this.brokerHost = brokerHost;
		this.topicBase = topicBase;
		this.processName = processName;
		this.ps = PublishSubject.create();
	}
	
	@Override
	public Observable<XTrace> getObservable() {
		return ps;
	}

	@Override
	public void prepare() throws SourceException {
		MqttConnectOptions options = new MqttConnectOptions();
		options.setCleanSession(true);
		options.setKeepAliveInterval(30);

		IMqttClient myClient;
		try {
			myClient = new MqttClient(brokerHost, UUID.randomUUID().toString());
			myClient.setCallback(new MqttCallback() {
				
				@Override
				public void messageArrived(String topic, MqttMessage message) throws Exception {
					int posLastSlash = topic.lastIndexOf("/");
					String partBeforeActName = topic.substring(0, posLastSlash);
					String activityName = topic.substring(posLastSlash + 1);
					String caseId = partBeforeActName.substring(partBeforeActName.lastIndexOf("/") + 1);
					ps.onNext(EventUtils.create(activityName, caseId));
				}
				
				@Override
				public void deliveryComplete(IMqttDeliveryToken token) {
					// no need to have anything here
				}
				
				@Override
				public void connectionLost(Throwable cause) {
					// no need to have anything here
				}
			});
			myClient.connect(options);
			myClient.subscribe(topicBase + "/" + processName + "/#");
		} catch (MqttException e) {
			throw new SourceException(e.getMessage());
		}
	}

}
