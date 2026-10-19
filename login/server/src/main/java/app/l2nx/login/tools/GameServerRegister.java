/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.login.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;

import app.l2nx.commons.database.DatabaseFactory;
import app.l2nx.commons.util.HexUtil;
import app.l2nx.login.data.GameServerTable;

/**
 * @author Skache
 */
public class GameServerRegister
{
	private GameServerRegister()
	{
		console();
	}
	
	private static String readLine(Scanner scanner)
	{
		return scanner.hasNextLine() ? scanner.nextLine().trim() : null;
	}

	private static File createAndRegister(int id, String outDir) throws IOException
	{
		final byte[] hexId = HexUtil.generateHexBytes(16); // Generate Hex ID.
		GameServerTable.getInstance().registerServerOnDB(hexId, id, ""); // Register the server in the database.
		
		final Properties hexSetting = new Properties();
		final File file = new File(outDir, "hexid.txt"); // Create the hexid.txt file.
		
		// Create a new empty file only if it doesn't exist.
		file.createNewFile();
		try (OutputStream out = new FileOutputStream(file))
		{
			hexSetting.setProperty("ServerID", String.valueOf(id));
			hexSetting.setProperty("HexID", new BigInteger(hexId).toString(16)); // Store the Hex ID as a string in the properties file.
			hexSetting.store(out, "The HexId to Auth into LoginServer");
		}

		return file;
	}
	
	private static void removeServer(int id) throws SQLException
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement statement = con.prepareStatement("DELETE FROM gameservers WHERE server_id = ?"))
		{
			statement.setInt(1, id);
			statement.executeUpdate();
		}
		
		GameServerTable.getInstance().getRegisteredGameServers().remove(id); // Remove it from the GameServerTable.
	}
	
	private static void removeAllServers() throws SQLException
	{
		try (Connection con = DatabaseFactory.getConnection();
			Statement statement = con.createStatement())
		{
			statement.executeUpdate("DELETE FROM gameservers");
			
			GameServerTable.getInstance().getRegisteredGameServers().clear();
		}
	}
	
	private void console()
	{
		System.out.println("=========================================================");
		System.out.println("              nx-l2 - Game Server Register               ");
		System.out.println("               Created by Skache                         ");
		System.out.println("=========================================================");
		
		showMenu();
		try (Scanner scanner = new Scanner(System.in))
		{
			boolean running = true;
			while (running)
			{
				System.out.print(System.lineSeparator() + "Enter your choice: ");

				if (!scanner.hasNextLine())
				{
					System.out.println(System.lineSeparator() + "[INFO] End of input. Exiting.");
					break;
				}

				if (!scanner.hasNextInt())
				{
					System.out.println(System.lineSeparator() + "[ERROR] Invalid input. Please enter a number.");
					scanner.nextLine(); // Clear invalid input.
					continue;
				}
				
				final int choice = scanner.nextInt();
				scanner.nextLine();
				
				switch (choice)
				{
					case 1:
					{
						System.out.println(System.lineSeparator() + "[INFO] You selected: List Servers");
						listServersConsole();
						break;
					}
					case 2:
					{
						System.out.println(System.lineSeparator() + "[INFO] You selected: Register Server");
						registerServerConsole(scanner);
						break;
					}
					case 3:
					{
						System.out.println(System.lineSeparator() + "[INFO] You selected: Remove Server");
						removeServerConsole(scanner);
						break;
					}
					case 4:
					{
						System.out.println(System.lineSeparator() + "[INFO] You selected: Remove All Servers");
						removeAllServersConsole(scanner);
						break;
					}
					case 5:
					{
						System.out.println(System.lineSeparator() + "[EXIT] Exiting the application. Goodbye!");
						running = false;
						break;
					}
					default:
					{
						System.out.println(System.lineSeparator() + "[WARNING] Invalid choice. Please select a valid option.");
						break;
					}
				}
				
				if (running)
				{
					showMenu();
				}
			}
		}
		finally
		{
			// Cleanup resources when exiting console mode.
			System.out.println("[INFO] Cleaning up resources...");
			DatabaseFactory.close();
		}
	}
	
	private void showMenu()
	{
		System.out.println(System.lineSeparator() + "=========================================================");
		System.out.println("                  AVAILABLE COMMANDS                     ");
		System.out.println("---------------------------------------------------------");
		System.out.println("  [1] List Servers");
		System.out.println("  [2] Register Server");
		System.out.println("  [3] Remove Server");
		System.out.println("  [4] Remove All Servers");
		System.out.println("  [5] Exit");
		System.out.println("---------------------------------------------------------");
	}
	
	private void listServersConsole()
	{
		final Map<Integer, String> serverNames = GameServerTable.getInstance().getServerNames();
		if (serverNames.isEmpty())
		{
			System.out.println("No game servers found.");
			return;
		}
		
		System.out.println(System.lineSeparator() + "List Servers:");
		System.out.println("+----------------+------------------------+-----------+");
		System.out.printf("| %-14s | %-22s | %-9s |" + System.lineSeparator(), "Server ID", "Server Name", "Status");
		System.out.println("+----------------+------------------------+-----------+");
		
		for (Entry<Integer, String> entry : serverNames.entrySet())
		{
			final int id = entry.getKey();
			final String serverName = entry.getValue();
			final boolean inUse = GameServerTable.getInstance().hasRegisteredGameServerOnId(id);
			final String status = inUse ? "In Use" : "Free";
			
			System.out.printf("| %-14d | %-22s | %-9s |" + System.lineSeparator(), id, serverName, status);
		}
		
		System.out.println("+----------------+------------------------+-----------+");
	}
	
	private void registerServerConsole(Scanner scanner)
	{
		System.out.print("Enter Game Server ID to register: ");
		final String input = readLine(scanner);
		if (input == null)
		{
			System.out.println("No input provided.");
			return;
		}

		// Check if the input is empty.
		if (input.isEmpty())
		{
			System.out.println("Game Server ID cannot be empty.");
			return;
		}
		
		try
		{
			// Ensure the ID is a valid number.
			final int id = Integer.parseInt(input);
			
			// Validate the ID range (positive IDs are valid).
			if (id <= 0)
			{
				System.out.println("Game Server ID must be a positive number.");
				return;
			}
			
			// Check if a server with the given ID exists.
			final String serverName = GameServerTable.getInstance().getServerNameById(id);
			
			// If no server name is found.
			if (serverName == null)
			{
				System.out.println("No server found for ID: " + id);
				return;
			}
			
			// If the server ID is already registered.
			if (GameServerTable.getInstance().hasRegisteredGameServerOnId(id))
			{
				System.out.println("Server '" + serverName + "' with ID " + id + " is already registered.");
				return;
			}
			
			final File hexIdFile = createAndRegister(id, ".");
			System.out.println("Game server with ID: " + id + " (" + serverName + ") has been successfully registered.");
			System.out.println("HexID written to " + hexIdFile.getAbsoluteFile().toPath().normalize() + " - copy it to the game server's config/hexid.txt.");
		}
		catch (NumberFormatException e)
		{
			System.out.println("Invalid Game Server ID entered. Please enter a valid number.");
		}
		catch (IOException e)
		{
			System.out.println("An error occurred while trying to register the Game Server: " + e.getMessage());
		}
	}
	
	private void removeServerConsole(Scanner scanner)
	{
		System.out.print("Enter Server ID to remove: ");
		final String input = readLine(scanner);
		if (input == null)
		{
			System.out.println("No input provided.");
			return;
		}

		// Check if the input is empty or invalid.
		if (input.isEmpty())
		{
			System.out.println("Server ID cannot be empty.");
			return;
		}
		
		try
		{
			final int serverId = Integer.parseInt(input);
			if (!GameServerTable.getInstance().hasRegisteredGameServerOnId(serverId))
			{
				System.out.println("No Game Server is registered with ID " + serverId + ".");
				return;
			}
			
			final String serverName = GameServerTable.getInstance().getServerNameById(serverId);
			
			// Confirm the removal.
			System.out.print("Are you sure you want to remove Game Server " + serverId + " (" + serverName + ")? (y/n): ");
			final String response = readLine(scanner);
			if ((response == null) || !response.equalsIgnoreCase("y"))
			{
				System.out.println("Operation canceled.");
				return;
			}

			removeServer(serverId);
			System.out.println("Game Server ID " + serverId + " (" + serverName + ") has been successfully removed.");
		}
		catch (NumberFormatException e)
		{
			System.out.println("Invalid Server ID. Please enter a valid number.");
		}
		catch (SQLException e)
		{
			System.out.println("Error removing server from the database: " + e.getMessage());
		}
		catch (Exception e)
		{
			System.out.println("An unexpected error occurred: " + e.getMessage());
		}
	}
	
	private void removeAllServersConsole(Scanner scanner)
	{
		// Ask for confirmation.
		System.out.print("Are you sure you want to remove all servers? (y/n): ");
		final String response = readLine(scanner);

		// Early return if the user doesn't confirm.
		if ((response == null) || !response.equalsIgnoreCase("y"))
		{
			System.out.println("Operation canceled.");
			return;
		}
		
		try
		{
			removeAllServers();
			System.out.println("All servers have been successfully removed.");
		}
		catch (SQLException e)
		{
			System.out.println("Error removing all servers: " + e.getMessage());
		}
		catch (Exception e)
		{
			System.out.println("An unexpected error occurred: " + e.getMessage());
		}
	}
	
	public static void main(String[] args)
	{
		DatabaseFactory.init();
		GameServerTable.getInstance();
		new GameServerRegister();
	}
}
