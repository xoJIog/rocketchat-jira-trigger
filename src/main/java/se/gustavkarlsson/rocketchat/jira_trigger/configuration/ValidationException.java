package se.gustavkarlsson.rocketchat.jira_trigger.configuration;

public class ValidationException extends Exception {
	ValidationException(Exception cause) {
		super(cause);
	}
}
