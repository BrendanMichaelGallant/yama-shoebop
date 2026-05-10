/*
 * Copyright (c) 2026, Wren <https://github.com/wren>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.wren.yamashoebop;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
		name = "Yama Shoebop",
		description = "Plays a short audio clip when casting the Leagues VI home teleport, and mutes the in-game teleport sound while it plays.",
		tags = {"leagues", "teleport", "sound", "audio", "demonic", "pacts"}
)
public class YamaShoebopPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(YamaShoebopPlugin.class);

	// Animation ID captured in-game for the League Home Teleport (Demonic Pacts).
	private static final int LEAGUE_HOME_TELEPORT_ANIM = 13764;

	private static final String AUDIO_RESOURCE = "/com/wren/yamashoebop/shoebop.wav";

	// Cast is ~12 seconds; window covers the full duration plus a small buffer
	// so the trailing teleport SFX is also suppressed.
	private static final long MUTE_WINDOW_MS = 13_000L;

	// Gain in dB. -2 dB is roughly equivalent to 80% linear volume.
	private static final float GAIN_DB = -2f;

	private static final int NO_ANIMATION = -1;

	@Inject private Client client;
	@Inject private YamaShoebopConfig config;
	@Inject private AudioPlayer audioPlayer;

	private int lastSeenAnimation = NO_ANIMATION;
	private long muteWindowEndsAtMillis = 0L;

	@Provides
	YamaShoebopConfig provideConfig(ConfigManager cm)
	{
		return cm.getConfig(YamaShoebopConfig.class);
	}

	@Override
	protected void shutDown()
	{
		muteWindowEndsAtMillis = 0L;
		lastSeenAnimation = NO_ANIMATION;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!config.enabled())
		{
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null || event.getActor() != local)
		{
			return;
		}

		int anim = local.getAnimation();
		if (anim == lastSeenAnimation)
		{
			return;
		}
		lastSeenAnimation = anim;

		if (anim == LEAGUE_HOME_TELEPORT_ANIM)
		{
			triggerShoebop();
		}
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		if (config.enabled() && System.currentTimeMillis() < muteWindowEndsAtMillis)
		{
			event.consume();
		}
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		Player local = client.getLocalPlayer();
		if (config.enabled()
				&& local != null
				&& event.getSource() == local
				&& System.currentTimeMillis() < muteWindowEndsAtMillis)
		{
			event.consume();
		}
	}

	private void triggerShoebop()
	{
		muteWindowEndsAtMillis = System.currentTimeMillis() + MUTE_WINDOW_MS;

		try
		{
			audioPlayer.play(YamaShoebopPlugin.class, AUDIO_RESOURCE, GAIN_DB);
		}
		catch (Exception e)
		{
			log.warn("Failed to play Yama Shoebop clip", e);
		}
	}
}