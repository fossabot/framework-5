package beamline.sources;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.extension.std.XTimeExtension;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryNaiveImpl;
import org.deckfour.xes.in.XParser;
import org.deckfour.xes.in.XesXmlGZIPParser;
import org.deckfour.xes.in.XesXmlParser;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;

import beamline.exceptions.SourceException;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;

/**
 * This implementation of a {@link XesSource} produces events according to the
 * events contained in an {@link XLog}. The events are first sorted according to
 * their timestamp and then sent. This source produces a cold observable.
 * 
 * @author Andrea Burattin
 */
public class XesLogSource implements XesSource {

	private static XFactory xesFactory = new XFactoryNaiveImpl();
	
	private String fileName;
	private XLog log;
	private List<XTrace> events;
	
	/**
	 * Constructs a source from the path of a log
	 * 
	 * @param fileName the file containing the log to use. The file can be
	 * either a file parsed by {@link XesXmlGZIPParser} or {@link XesXmlParser}
	 * (i.e., extensions <code>.xes.gz</code> or <code>.xes</code>). If the file
	 * is none of these, then {@link #prepare()} will throw an exception.
	 */
	public XesLogSource(String fileName) {
		this.fileName = fileName;
	}
	
	/**
	 * Constructs a source from the given log
	 * 
	 * @param log the log to use as source
	 */
	public XesLogSource(XLog log) {
		this.log = log;
	}
	
	@Override
	public Observable<XTrace> getObservable() {
		return Observable.create(new ObservableOnSubscribe<XTrace>() {
			@Override
			public void subscribe(@NonNull ObservableEmitter<@NonNull XTrace> emitter) throws Throwable {
				for (XTrace wrapper : events) {
					emitter.onNext(wrapper);
				}
				emitter.onComplete();
			}
		});
	}
	
	@Override
	public void prepare() throws SourceException {
		if (log == null) {
			parseLog(fileName);
		}
		prepareStream();
	}
	
	private void parseLog(String fileName) throws SourceException {
		XParser[] parsers = new XParser[] { new XesXmlGZIPParser(), new XesXmlParser() };
		File file = new File(fileName);
		for (XParser p : parsers) {
			if (p.canParse(file)) {
				try {
					log = p.parse(file).get(0);
				} catch (Exception e) {
					throw new SourceException(e.getMessage());
				}
				return;
			}
		}
		throw new SourceException("XES file format not supported");
	}
	
	private void prepareStream() throws SourceException {
		if (log.isEmpty()) {
			throw new SourceException("The given log is empty");
		}
		// populate all events
		events = new LinkedList<>();
		for (XTrace t : log) {
			for (XEvent e : t) {
				// create the wrapping trace
				XTrace eventWrapper = xesFactory.createTrace();
				XAttributeMap am = t.getAttributes();
				for (Map.Entry<String, XAttribute> v : am.entrySet()) {
					eventWrapper.getAttributes().put(v.getKey(), v.getValue());
				}
				// create the actual event
				XEvent newEvent = xesFactory.createEvent();
				XAttributeMap amEvent = e.getAttributes();
				for (Map.Entry<String, XAttribute> v : amEvent.entrySet()) {
					newEvent.getAttributes().put(v.getKey(), v.getValue());
				}
				eventWrapper.add(newEvent);
				events.add(eventWrapper);
			}
		}
		
		// sort events
		Collections.sort(events, (XTrace o1, XTrace o2) -> {
			XEvent e1 = o1.get(0);
			XEvent e2 = o2.get(0);
			Date d1 = XTimeExtension.instance().extractTimestamp(e1);
			Date d2 = XTimeExtension.instance().extractTimestamp(e2);
			if (d1 == null || d2 == null) {
				return 0;
			}
			return d1.compareTo(d2);
		});
	}
}
