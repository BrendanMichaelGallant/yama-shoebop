/*
 * Copyright (c) 2026, Wren <https://github.com/wren>
 * All rights reserved.
 */
package com.wren.yamashoebop;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class YamaShoebopPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(YamaShoebopPlugin.class);
		RuneLite.main(args);
	}
}
