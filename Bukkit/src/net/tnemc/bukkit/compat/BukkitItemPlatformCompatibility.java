package net.tnemc.bukkit.compat;

/*
 * The New Economy
 * Copyright (C) 2022 - 2025 Daniel "creatorfromhell" Vidmar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import net.tnemc.item.bukkit.platform.BukkitItemPlatform;
import net.tnemc.item.platform.conversion.PlatformConverter;
import net.tnemc.plugincore.PluginCore;
import net.tnemc.plugincore.core.compatibility.log.DebugLevel;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * BukkitItemPlatformCompatibility
 *
 * @author creatorfromhell
 * @since 0.1.4.3
 */
public final class BukkitItemPlatformCompatibility {

  private static final String[] KEYED_SERIALIZATION_TYPES = {
    "org.bukkit.block.banner.PatternType",
    "org.bukkit.inventory.meta.trim.TrimPattern",
    "org.bukkit.inventory.meta.trim.TrimMaterial",
    "org.bukkit.potion.PotionEffectType"
  };

  private static volatile boolean applied = false;

  private BukkitItemPlatformCompatibility() {
  }

  public static synchronized void apply() {

    if(applied) return;

    final PlatformConverter converter = BukkitItemPlatform.instance().converter();

    converter.registerConversion(Enchantment.class, String.class, BukkitItemPlatformCompatibility::serializeEnchantment);
    converter.registerConversion(String.class, Enchantment.class, BukkitItemPlatformCompatibility::deserializeEnchantment);

    for(final String type : KEYED_SERIALIZATION_TYPES) {
      overrideKeyedSerialization(converter, type);
    }

    applied = true;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void overrideKeyedSerialization(final PlatformConverter converter, final String type) {

    try {
      final Class<?> clazz = Class.forName(type);
      converter.registerConversion((Class)clazz, String.class, BukkitItemPlatformCompatibility::serializeKeyed);
    } catch(final ClassNotFoundException ignored) {
      // Optional API type for older server versions.
    }
  }

  private static String serializeEnchantment(final Enchantment enchantment) {

    if(enchantment == null) return "unknown:null";

    NamespacedKey key = registryEnchantKey(enchantment);
    if(key == null) {
      key = enchantment.getKey();
    }

    if(key != null) {
      return key.toString();
    }

    return fallbackKey(enchantment);
  }

  private static Enchantment deserializeEnchantment(final String value) {

    if(value == null || value.isBlank()) {
      return null;
    }

    final String normalized = value.toLowerCase(Locale.ROOT);
    final NamespacedKey key = NamespacedKey.fromString(normalized);
    if(key != null) {
      final Enchantment byKey = Enchantment.getByKey(key);
      if(byKey != null) {
        return byKey;
      }
    }

    final Enchantment byName = Enchantment.getByName(value.toUpperCase(Locale.ROOT));
    if(byName == null && normalized.startsWith("unknown:")) {
      PluginCore.log().debug("Skipping unknown enchantment identifier during conversion: " + value, DebugLevel.DEVELOPER);
    }
    return byName;
  }

  private static String serializeKeyed(final Object value) {

    if(value instanceof Keyed keyed) {
      final NamespacedKey key = keyed.getKey();
      if(key != null) {
        return key.toString();
      }
    }

    return fallbackKey(value);
  }

  private static NamespacedKey registryEnchantKey(final Enchantment enchantment) {

    try {
      final Method keyMethod = Registry.class.getMethod("getKey", Keyed.class);
      final Object response = keyMethod.invoke(Registry.ENCHANTMENT, enchantment);
      if(response instanceof NamespacedKey) {
        return (NamespacedKey)response;
      }
    } catch(final Exception ignored) {
      // Registry#getKey is unavailable on older API versions.
    }
    return null;
  }

  private static String fallbackKey(final Object value) {

    final String fallback = "unknown:" + sanitize(String.valueOf(value));
    PluginCore.log().debug("Unable to resolve item key for conversion, using fallback: " + fallback, DebugLevel.DEVELOPER);
    return fallback;
  }

  private static String sanitize(final String value) {

    if(value == null || value.isBlank()) return "unknown";
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/\\-]", "_");
  }
}
