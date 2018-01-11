package se.gustavkarlsson.rocketchat.jira_trigger.routes;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Sets.union;
import static org.apache.commons.lang3.Validate.notNull;
import static org.slf4j.LoggerFactory.getLogger;

public class JiraKeyParser {
	private String jiraUri; 
	private static final Set<Character> ALWAYS_VALID = new HashSet<>(Arrays.asList(' ', '\t', '\n'));

	private final Set<Character> whitelistedPrefixes;
	private final Set<Character> whitelistedSuffixes;
	private Pattern jiraKey;

	public JiraKeyParser(Set<Character> whitelistedPrefixes, Set<Character> whitelistedSuffixes, String jiraUri) {
		this.whitelistedPrefixes = union(notNull(whitelistedPrefixes), ALWAYS_VALID);
		this.whitelistedSuffixes = union(notNull(whitelistedSuffixes), ALWAYS_VALID);
		this.jiraUri = jiraUri;
	}

	private static Set<Pair<Character, Character>> parseContexts(String key, String text) {
		Set<Pair<Character, Character>> contexts = new HashSet<>();
		int fromIndex = 0;
		while (true) {
			int beginIndex = text.indexOf(key, fromIndex);
			if (beginIndex == -1) {
				break;
			}
			int endIndex = beginIndex + key.length();
			Character prefix = beginIndex > 0 ? text.charAt(beginIndex - 1) : ' ';
			Character suffix = endIndex < text.length() ? text.charAt(endIndex) : ' ';
			contexts.add(new ImmutablePair<>(prefix, suffix));
			fromIndex = endIndex;
		}
		return contexts;
	}
	private Pattern getJiraKey() {
		if ( this.jiraKey == null ) { 
			this.jiraKey = Pattern.compile("(?:" + Pattern.quote(this.jiraUri+"/browse/") + ")?([A-Z]+-\\d+\\+?)");
		}
		return this.jiraKey;
	}
	Map<String, Boolean> parse(String text) {
		Matcher matcher = this.getJiraKey().matcher(text);
		Map<String, Boolean> jiraKeys = new HashMap<>();
		while (matcher.find()) {
			String key = matcher.group(1);
			if (!hasValidContext(key, text)) {
				continue;
			}
			boolean extended = key.endsWith("+");
			if (extended) {
				key = key.substring(0, key.length() - 1);
			}
			jiraKeys.put(key, extended);
		}
		return jiraKeys;
	}

	private boolean hasValidContext(String key, String text) {
		Set<Pair<Character, Character>> contextsForKey = parseContexts(key, text);
		return contextsForKey.stream().anyMatch(this::isValidContext);
	}

	private boolean isValidContext(Pair<Character, Character> context) {
		return whitelistedPrefixes.contains(context.getLeft()) && whitelistedSuffixes.contains(context.getRight());
	}
}
