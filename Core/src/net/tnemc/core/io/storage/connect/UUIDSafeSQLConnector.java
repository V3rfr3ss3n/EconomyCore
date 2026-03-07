package net.tnemc.core.io.storage.connect;

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

import net.tnemc.plugincore.core.io.storage.connect.SQLConnector;

import java.sql.ResultSet;
import java.util.UUID;

/**
 * UUIDSafeSQLConnector
 *
 * Ensures UUID parameters are always bound as canonical strings.
 *
 * @author creatorfromhell
 * @since 0.1.4.3
 */
public class UUIDSafeSQLConnector extends SQLConnector {

  @Override
  public ResultSet executeQuery(final String query, final Object[] variables) {

    return super.executeQuery(query, normalizeVariables(variables));
  }

  @Override
  public int executeUpdate(final String query, final Object[] variables) {

    return super.executeUpdate(query, normalizeVariables(variables));
  }

  private Object[] normalizeVariables(final Object[] variables) {

    if(variables == null || variables.length == 0) {
      return variables;
    }

    final Object[] normalized = new Object[variables.length];
    for(int i = 0; i < variables.length; i++) {
      final Object value = variables[i];
      normalized[i] = (value instanceof UUID uuid)? uuid.toString() : value;
    }
    return normalized;
  }
}
