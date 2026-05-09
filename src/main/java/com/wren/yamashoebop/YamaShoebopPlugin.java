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
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Yama Shoebop",
	description = "Plays a short audio clip when casting the Leagues VI home teleport, and mutes the in-game teleport sound while it plays.",
	tags = {"leagues", "teleport", "sound", "audio", "demonic", "pacts"}
)
public class YamaShoebopPlugin extends Plugin
{
	// Animation ID captured in-game for the League Home Teleport (Demonic Pacts).
	private static final int LEAGUE_HOME_TELEPORT_ANIM = 13764;

	private static final String AUDIO_RESOURCE = "/com/wren/yamashoebop/shoebop.wav";
	private static final long MUTE_WINDOW_MS = 4000L;
	private static final float VOLUME_LINEAR = 0.8f;
	private static final int NO_ANIMATION = -1;

	@Inject private Client client;
	@Inject private YamaShoebopConfig config;
	@Inject private ScheduledExecutorService executor;

	private Clip activeClip;
	private int lastSeenAnimation = NO_ANIMATION;
	private long muteWindowEndsAtMillis = 0L;
	private WorldPoint lastTickLocation;

	@Provides
	YamaShoebopConfig provideConfig(ConfigManager cm)
	{
		return cm.getConfig(YamaShoebopConfig.class);
	}

	@Override
	protected void shutDown()
	{
		stopClip();
		muteWindowEndsAtMillis = 0L;
		lastTickLocation = null;
		lastSeenAnimation = NO_ANIMATION;
	}

	// --------- Detection ---------

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

	// --------- Stop on movement ---------

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			lastTickLocation = null;
			return;
		}

		WorldPoint current = local.getWorldLocation();

		if (activeClip != null && activeClip.isRunning()
			&& lastTickLocation != null && !lastTickLocation.equals(current))
		{
			stopClip();
			muteWindowEndsAtMillis = 0L;
		}

		lastTickLocation = current;
	}

	// --------- Mute the in-game teleport sound while our clip is playing ---------

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

	// --------- Audio playback ---------

	private void triggerShoebop()
	{
		muteWindowEndsAtMillis = System.currentTimeMillis() + MUTE_WINDOW_MS;
		executor.execute(this::playEmbeddedClip);
	}

	private void playEmbeddedClip()
	{
		stopClip();

		try (InputStream raw = YamaShoebopPlugin.class.getResourceAsStream(AUDIO_RESOURCE))
		{
			if (raw == null)
			{
				log.warn("Bundled audio resource not found: {}", AUDIO_RESOURCE);
				return;
			}

			try (BufferedInputStream buffered = new BufferedInputStream(raw);
				AudioInputStream in = AudioSystem.getAudioInputStream(buffered))
			{
				Clip clip = AudioSystem.getClip();
				clip.open(in);

				if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
				{
					FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
					float v = Math.max(0f, Math.min(1f, VOLUME_LINEAR));
					float dB = v <= 0.0001f ? -60f : (float) (20.0 * Math.log10(v));
					gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB)));
				}

				activeClip = clip;
				clip.start();
			}
		}
		catch (UnsupportedAudioFileException e)
		{
			log.warn("Bundled audio is not a supported PCM WAV format", e);
		}
		catch (LineUnavailableException | IOException e)
		{
			log.warn("Failed to play Yama Shoebop clip", e);
		}
	}

	private void stopClip()
	{
		if (activeClip != null)
		{
			try
			{
				if (activeClip.isRunning())
				{
					activeClip.stop();
				}
				activeClip.close();
			}
			catch (Exception ignored)
			{
			}
			activeClip = null;
		}
	}
}
