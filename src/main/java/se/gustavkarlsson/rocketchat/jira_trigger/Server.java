package se.gustavkarlsson.rocketchat.jira_trigger;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.MetadataRestClient;
import com.atlassian.jira.rest.client.api.domain.ServerInfo;
import com.atlassian.util.concurrent.Promise;
import org.slf4j.Logger;
import se.gustavkarlsson.rocketchat.jira_trigger.configuration.*;
import se.gustavkarlsson.rocketchat.jira_trigger.converters.AttachmentConverter;
import se.gustavkarlsson.rocketchat.jira_trigger.converters.ToRocketChatMessageFactory;
import se.gustavkarlsson.rocketchat.jira_trigger.converters.fields.FieldExtractor;
import se.gustavkarlsson.rocketchat.jira_trigger.routes.DetectIssueRoute;
import se.gustavkarlsson.rocketchat.jira_trigger.routes.JiraKeyParser;
import spark.Request;
import spark.Response;
import spark.Service;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;
import static spark.Service.ignite;

class Server {
	private static final Logger log = getLogger(Server.class);

	private static final String APPLICATION_JSON = "application/json";

	private final JiraRestClient jiraClient;
	private final DetectIssueRoute detectIssueRoute;
	private final int maxThreads;
	private final int port;
	private Service server;

	Server(Configuration config) {
		JiraConfiguration jiraConfig = config.getJiraConfiguration();
		MessageConfiguration messageConfig = config.getMessageConfiguration();
		AppConfiguration appConfig = config.getAppConfiguration();
		RocketChatConfiguration rocketChatConfig = config.getRocketChatConfiguration();

		RestClientProvider restClientProvider = new RestClientProvider();
		ToRocketChatMessageFactory toRocketChatMessageFactory = new ToRocketChatMessageFactory(messageConfig);
		FieldExtractorMapper fieldExtractorMapper = new FieldExtractorMapper(messageConfig);
		List<FieldExtractor> defaultFieldExtractors = fieldExtractorMapper.getCreators(messageConfig.getDefaultFields());
		List<FieldExtractor> extendedFieldExtractors = fieldExtractorMapper.getCreators(messageConfig.getExtendedFields());
		AttachmentConverter attachmentConverter = new AttachmentConverter(messageConfig, defaultFieldExtractors, extendedFieldExtractors);
		JiraKeyParser issueParser = new JiraKeyParser(messageConfig.getWhitelistedJiraKeyPrefixes(), messageConfig.getWhitelistedJiraKeySuffixes(),jiraConfig.getUri().toString());

		jiraClient = restClientProvider.get(jiraConfig);
		maxThreads = appConfig.getMaxThreads();
		port = appConfig.getPort();
		detectIssueRoute = new DetectIssueRoute(rocketChatConfig, jiraClient.getIssueClient(), toRocketChatMessageFactory, attachmentConverter, issueParser);
	}

	private static void logRequest(Request request) {
		log.info("Incoming request: {} {} {} {}",
				request.raw().getRemoteAddr(), request.requestMethod(), request.contentType(), request.pathInfo());
	}

	private static void logResponse(Response response) {
		String responseContent = response.body() == null ? "<empty>" : "Rocket.Chat message";
		log.info("Outgoing response: {}", responseContent);
	}

	void logJiraServerInfo() {
		MetadataRestClient metadataClient = jiraClient.getMetadataClient();
		Promise<ServerInfo> serverInfoPromise = metadataClient.getServerInfo();
		try {
			ServerInfo serverInfo = serverInfoPromise.get();
			log.info("Found JIRA instance '{}' running version {} at {}",
					serverInfo.getServerTitle(),
					serverInfo.getVersion(),
					serverInfo.getBaseUri());
		} catch (Exception e) {
			log.warn("Failed to connect to JIRA", e);
		}
	}

	synchronized void start() {
		log.info("Starting server...");
		if (server != null) {
			throw new IllegalStateException("Already started");
		}
		Service server = ignite();
		server.threadPool(maxThreads);
		server.port(port);
		server.before((request, response) -> logRequest(request));
		server.post("/", APPLICATION_JSON, detectIssueRoute);
		server.after((request, response) -> logResponse(response));
		server.exception(Exception.class, new UuidGeneratingExceptionHandler());
		this.server = server;
	}

	synchronized void stop() {
		if (server == null) {
			throw new IllegalStateException("Already stopped");
		}
		server.stop();
		server = null;
	}
}
