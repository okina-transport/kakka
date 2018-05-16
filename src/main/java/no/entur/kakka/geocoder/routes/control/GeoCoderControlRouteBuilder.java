/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.entur.kakka.geocoder.routes.control;

import no.entur.kakka.Constants;
import no.entur.kakka.geocoder.BaseRouteBuilder;
import no.entur.kakka.geocoder.GeoCoderConstants;
import no.entur.kakka.routes.status.JobEvent;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.stream.Collectors;


//@Component
public class GeoCoderControlRouteBuilder extends BaseRouteBuilder {


	private static final String TASK_MESSAGE = "RutebankenGeoCoderTaskMessage";

	@Value("${geocoder.max.retries:600}")
	private int maxRetries;

	@Value("${geocoder.retry.delay:15000}")
	private int retryDelay;

	private GeoCoderTaskMessage createMessageFromTaskTypes(Collection<GeoCoderTaskType> taskTypes) {
		return new GeoCoderTaskMessage(taskTypes.stream().map(t -> t.getGeoCoderTask()).collect(Collectors.toList()));
	}

	@Override
	public void configure() throws Exception {
		super.configure();

		from("direct:geoCoderStart")
				.process(e -> e.getIn().setBody(new GeoCoderTaskMessage(e.getIn().getBody(GeoCoderTask.class)).toString()))
				.to("activemq:queue:GeoCoderQueue")
				.routeId("geocoder-start");

		from("direct:geoCoderStartBatch")
				.process(e -> e.getIn().setBody(createMessageFromTaskTypes(e.getIn().getBody(Collection.class)).toString()))
				.to("activemq:queue:GeoCoderQueue")
				.routeId("geocoder-start-batch");


		singletonFrom("activemq:queue:GeoCoderQueue?transacted=true&messageListenerContainerFactoryRef=batchListenerContainerFactory")
				.autoStartup("{{geocoder.autoStartup:true}}")
				.transacted()
				.to("direct:geoCoderMergeTaskMessages")
				.setProperty(TASK_MESSAGE, simple("${body}"))
				.to("direct:geoCoderRehydrate")

				.to("direct:geoCoderDelayIfRetry")
				.log(LoggingLevel.INFO, getClass().getName(), "Processing: ${body}. QueuedTasks: ${exchangeProperty." + TASK_MESSAGE + ".tasks.size}")
				.toD("${body.endpoint}")


				.choice()
				.when(simple("${exchangeProperty." + GeoCoderConstants.GEOCODER_RESCHEDULE_TASK + "}"))
				.to("direct:geoCoderRescheduleTask")
				.otherwise()
				.removeHeader(Constants.LOOP_COUNTER)
				.end()

				.setBody(simple("${exchangeProperty." + TASK_MESSAGE + "}"))
				.to("direct:geoCoderDehydrate")

				.choice()
				.when(simple("${body.complete}"))
				.log(LoggingLevel.INFO, getClass().getName(), "GeoCoder route completed")
				.otherwise()
				.convertBodyTo(String.class)
				.to("activemq:queue:GeoCoderQueue")
				.end()

				.routeId("geocoder-main-route");

		from("direct:geoCoderDelayIfRetry")
				.choice()
				.when(simple("${header." + Constants.LOOP_COUNTER + "} > 0"))
				.log(LoggingLevel.INFO, getClass().getName(), "Delay processing of: ${body}. Retry no: ${header." + Constants.LOOP_COUNTER + "}")
				.delay(retryDelay)
				.end()
				.routeId("geocoder-delay-retry");

		from("direct:geoCoderMergeTaskMessages")
				.process(e -> e.getIn().setBody(merge(e.getIn().getBody(Collection.class))))
				.routeId("geocoder-merge-messages");

		from("direct:geoCoderRehydrate")
				.process(e -> rehydrate(e))
				.routeId("geocoder-rehydrate-task");

		from("direct:geoCoderDehydrate")
				.process(e -> dehydrate(e))
				.routeId("geocoder-dehydrate-task");

		from("direct:geoCoderRescheduleTask")
				.process(e -> e.getIn().setHeader(Constants.LOOP_COUNTER, (Integer) e.getIn().getHeader(Constants.LOOP_COUNTER, 0) + 1))
				.choice()
				.when(simple("${header." + Constants.LOOP_COUNTER + "} > " + maxRetries))
				.log(LoggingLevel.WARN, getClass().getName(), "${header." + GeoCoderConstants.GEOCODER_CURRENT_TASK + "} timed out. Config should probably be tweaked. Not rescheduling.")
				.process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.TIMEOUT).build()).to("direct:updateStatus")
				.otherwise()
				.setProperty(GeoCoderConstants.GEOCODER_NEXT_TASK, simple("${header." + GeoCoderConstants.GEOCODER_CURRENT_TASK + "}"))
				.end()
				.routeId("geocoder-reschedule-task");
	}


	private void rehydrate(Exchange e) {
		GeoCoderTaskMessage msg = e.getIn().getBody(GeoCoderTaskMessage.class);
		GeoCoderTask task = msg.popNextTask();
		task.getHeaders().forEach((k, v) -> e.getIn().setHeader(k, v));
		e.setProperty(GeoCoderConstants.GEOCODER_CURRENT_TASK, task);
		e.getIn().setBody(task);
	}


	private void dehydrate(Exchange e) {
		GeoCoderTask nextTask = e.getProperty(GeoCoderConstants.GEOCODER_NEXT_TASK, GeoCoderTask.class);
		if (nextTask != null) {
			e.getIn().getBody(GeoCoderTaskMessage.class).addTask(nextTask);
			e.getIn().getHeaders().entrySet().stream().filter(entry -> entry.getKey().startsWith("Rutebanken")).forEach(entry -> nextTask.getHeaders().put(entry.getKey(), entry.getValue()));
		}
	}


	private GeoCoderTaskMessage merge(Collection<ActiveMQTextMessage> messages) {
		GeoCoderTaskMessage merged = new GeoCoderTaskMessage();

		if (!CollectionUtils.isEmpty(messages)) {
			for (ActiveMQTextMessage msg : messages) {
				try {
					// TODO merge smarter, keep oldest. log discard?
					GeoCoderTaskMessage taskMessage = GeoCoderTaskMessage.fromString(msg.getText());
					merged.getTasks().addAll(taskMessage.getTasks());
				} catch (Exception e) {
					log.warn("Discarded unparseable text msg: " + msg + ". Exception:" + e.getMessage(), e);
				}
			}
		}

		return merged;
	}

}
