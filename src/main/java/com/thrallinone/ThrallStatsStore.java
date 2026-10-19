package com.thrallinone;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Loads and saves {@link ThrallRecords} for the logged-in account, as JSON
 * under an rsprofile key, so each account keeps its own records.
 */
@Slf4j
@Singleton
class ThrallStatsStore
{
	static final String RECORDS_KEY = "records";

	private final ConfigManager configManager;
	/** The injected Gson with enum map keys (see {@link #withEnumKeys}), built once. */
	private final Gson gson;

	@Getter
	private ThrallRecords records = new ThrallRecords();
	/** The account the records belong to; null while logged out or while its save is unreadable. */
	@Nullable
	private String profileKey;
	/** The account whose save couldn't be read: left alone, unless the player resets their records. */
	@Nullable
	private String unreadableKey;

	@Inject
	ThrallStatsStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = withEnumKeys(gson);
	}

	/** Load the current account's records (empty while logged out). */
	void load()
	{
		profileKey = configManager.getRSProfileKey();
		unreadableKey = null;
		String json = profileKey == null ? null : configManager.getRSProfileConfiguration(ThrallInOneConfig.GROUP, RECORDS_KEY);
		ThrallRecords loaded = read(gson, json);
		if (loaded == null)
		{
			// Keep the unreadable save rather than overwrite it with empty records.
			log.warn("Couldn't read the saved thrall records; they won't be updated until they're reset");
			unreadableKey = profileKey;
			profileKey = null;
			loaded = new ThrallRecords();
		}
		records = loaded;
		log.debug("Loaded thrall records for profile {}: {} fights, {} summons",
			profileKey, records.getLifetime().getFights(), records.getSummons());
	}

	void save()
	{
		// Never write one account's records under another's key.
		if (profileKey == null || !profileKey.equals(configManager.getRSProfileKey()))
		{
			return;
		}
		configManager.setRSProfileConfiguration(ThrallInOneConfig.GROUP, RECORDS_KEY, gson.toJson(records));
	}

	/** Clear the account's records, including a save that couldn't be read; saving works again afterwards. */
	void reset()
	{
		records = new ThrallRecords();
		String current = configManager.getRSProfileKey();
		if (unreadableKey != null && unreadableKey.equals(current))
		{
			profileKey = unreadableKey;
			unreadableKey = null;
		}
		if (profileKey != null && profileKey.equals(current))
		{
			configManager.unsetRSProfileConfiguration(ThrallInOneConfig.GROUP, RECORDS_KEY);
		}
	}

	/** A copy for another thread (the sidebar panel) to read. */
	ThrallRecords copy()
	{
		return read(gson, gson.toJson(records));
	}

	static String toJson(Gson gson, ThrallRecords records)
	{
		return withEnumKeys(gson).toJson(records);
	}

	/**
	 * Gson writes plain map keys with toString() but reads enum keys by name,
	 * so a map keyed by an enum with a display toString (ThrallTier) wouldn't
	 * read back. Complex map key serialization writes keys through their type
	 * adapter, i.e. by name, the same JSON as before for every other key.
	 */
	private static Gson withEnumKeys(Gson gson)
	{
		return gson.newBuilder().enableComplexMapKeySerialization().create();
	}

	/** Records from JSON: empty for no save, null if the save can't be read. */
	@Nullable
	static ThrallRecords fromJson(Gson gson, @Nullable String json)
	{
		return read(withEnumKeys(gson), json);
	}

	@Nullable
	private static ThrallRecords read(Gson recordsGson, @Nullable String json)
	{
		if (json == null || json.isEmpty())
		{
			return new ThrallRecords();
		}
		try
		{
			ThrallRecords records = recordsGson.fromJson(json, ThrallRecords.class);
			return records == null ? new ThrallRecords() : records.repair();
		}
		catch (JsonParseException | IllegalStateException e)
		{
			log.debug("Unreadable thrall records", e);
			return null;
		}
	}
}
