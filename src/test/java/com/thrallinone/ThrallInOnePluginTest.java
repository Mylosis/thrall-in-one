package com.thrallinone;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ThrallInOnePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ThrallInOnePlugin.class);
		RuneLite.main(args);
	}
}
