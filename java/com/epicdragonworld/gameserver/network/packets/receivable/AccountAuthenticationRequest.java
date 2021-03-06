/*
 * This file is part of the Epic Dragon World project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.epicdragonworld.gameserver.network.packets.receivable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Logger;

import com.epicdragonworld.Config;
import com.epicdragonworld.gameserver.managers.DatabaseManager;
import com.epicdragonworld.gameserver.managers.WorldManager;
import com.epicdragonworld.gameserver.network.GameClient;
import com.epicdragonworld.gameserver.network.ReceivablePacket;
import com.epicdragonworld.gameserver.network.packets.sendable.AccountAuthenticationResult;
import com.epicdragonworld.gameserver.network.packets.sendable.Logout;
import com.epicdragonworld.util.Util;

/**
 * @author Pantelis Andrianakis
 */
public class AccountAuthenticationRequest
{
	private static final Logger LOGGER = Logger.getLogger(AccountAuthenticationRequest.class.getName());
	
	private static final String ACCOUNT_INFO_QUERY = "SELECT * FROM accounts WHERE account=?";
	private static final String ACCOUNT_INFO_UPDATE_QUERY = "UPDATE accounts SET last_active=?, last_ip=? WHERE account=?";
	private static final String ACCOUNT_CREATE_QUERY = "INSERT INTO accounts (account, password, status) values (?, ?, 3)";
	private static final int STATUS_NOT_FOUND = 0;
	private static final int STATUS_WRONG_PASSWORD = 3;
	private static final int STATUS_ALREADY_ONLINE = 4;
	private static final int STATUS_TOO_MANY_ONLINE = 5;
	private static final int STATUS_AUTHENTICATED = 100;
	
	public AccountAuthenticationRequest(GameClient client, ReceivablePacket packet)
	{
		// Read data.
		String accountName = packet.readString().toLowerCase();
		final String passwordHash = packet.readString();
		
		// Local data.
		for (char c : Util.ILLEGAL_CHARACTERS)
		{
			accountName = accountName.replace(c, '\'');
		}
		String storedPassword = "";
		int status = STATUS_NOT_FOUND;
		
		// Checks.
		if ((accountName.length() < 2) || (accountName.length() > 20) || accountName.contains("'") || (passwordHash.length() == 0)) // 20 should not happen, checking it here in case of client cheat.
		{
			client.channelSend(new AccountAuthenticationResult(STATUS_NOT_FOUND));
			return;
		}
		
		// Get data from database.
		try (Connection con = DatabaseManager.getInstance().getConnection();
			PreparedStatement ps = con.prepareStatement(ACCOUNT_INFO_QUERY))
		{
			ps.setString(1, accountName);
			try (ResultSet rset = ps.executeQuery())
			{
				while (rset.next())
				{
					storedPassword = rset.getString("password");
					status = rset.getInt("status");
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(e.getMessage());
		}
		
		// In case of auto create accounts configuration.
		if ((status == 0) && Config.ACCOUNT_AUTO_CREATE)
		{
			// Create account.
			try (Connection con = DatabaseManager.getInstance().getConnection();
				PreparedStatement ps = con.prepareStatement(ACCOUNT_CREATE_QUERY))
			{
				ps.setString(1, accountName);
				ps.setString(2, passwordHash);
				ps.execute();
			}
			catch (Exception e)
			{
				LOGGER.warning(e.getMessage());
			}
			LOGGER.info("Created account " + accountName + ".");
		}
		else // Account status issue.
		{
			// 0 does not exist, 1 banned, 2 requires activation, 3 wrong password, 4 too many online, 100 authenticated
			if (status < STATUS_WRONG_PASSWORD)
			{
				client.channelSend(new AccountAuthenticationResult(status));
				return;
			}
			
			// Wrong password.
			if (!passwordHash.equals(storedPassword))
			{
				client.channelSend(new AccountAuthenticationResult(STATUS_WRONG_PASSWORD));
				return;
			}
		}
		
		// Kick existing logged client.
		final GameClient existingClient = WorldManager.getInstance().getClientByAccountName(accountName);
		if (existingClient != null)
		{
			existingClient.channelSend(new Logout(accountName));
			client.channelSend(new AccountAuthenticationResult(STATUS_ALREADY_ONLINE));
			return;
		}
		
		// Too many online users.
		if (WorldManager.getInstance().getOnlineCount() >= Config.MAXIMUM_ONLINE_USERS)
		{
			client.channelSend(new AccountAuthenticationResult(STATUS_TOO_MANY_ONLINE));
			return;
		}
		
		// Authentication was successful.
		WorldManager.getInstance().addClient(client);
		client.setAccountName(accountName);
		client.channelSend(new AccountAuthenticationResult(STATUS_AUTHENTICATED));
		
		// Update last login date and IP address.
		try (Connection con = DatabaseManager.getInstance().getConnection();
			PreparedStatement ps = con.prepareStatement(ACCOUNT_INFO_UPDATE_QUERY))
		{
			ps.setLong(1, System.currentTimeMillis());
			ps.setString(2, client.getIp());
			ps.setString(3, accountName);
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.warning(e.getMessage());
		}
	}
}
