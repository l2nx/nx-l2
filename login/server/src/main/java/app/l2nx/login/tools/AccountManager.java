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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import app.l2nx.commons.database.DatabaseFactory;

/**
 * @author Skache
 */
public class AccountManager
{
	public AccountManager()
	{
		console();
	}
	
	private static final Set<Integer> VALID_ACCESS_LEVELS = new HashSet<>(Arrays.asList(-1, 0, 10, 20, 30, 40, 50, 60, 70, 100));

	private static String readLine(Scanner scanner)
	{
		return scanner.hasNextLine() ? scanner.nextLine().trim() : null;
	}

	private void console()
	{
		System.out.println("=========================================================");
		System.out.println("                 nx-l2 - Account Manager                 ");
		System.out.println("               Created by Skache                        ");
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
					scanner.nextLine(); // Clear invalid input
					continue;
				}
				
				final int choice = scanner.nextInt();
				scanner.nextLine();
				
				switch (choice)
				{
					case 1:
					{
						System.out.println(System.lineSeparator() + "[INFO] You selected: Create Account");
						createAccountCmd(scanner);
						break;
					}
					case 2:
					{
						System.out.println(System.lineSeparator() + "[INFO] You selected: Delete Account");
						deleteAccountCmd(scanner);
						break;
					}
					case 3:
					{
						System.out.println(System.lineSeparator() + "[INFO] You selected: Update Accounts");
						updateAccountCmd(scanner);
						break;
					}
					case 4:
					{
						System.out.println(System.lineSeparator() + "[INFO] You selected: List All Accounts");
						listAccountsCmd();
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
			
			// Close any open resources, like database connections.
			DatabaseFactory.close();
		}
	}
	
	private void showMenu()
	{
		System.out.println(System.lineSeparator() + "=========================================================");
		System.out.println("                  AVAILABLE COMMANDS                     ");
		System.out.println("---------------------------------------------------------");
		System.out.println("  [1] Create Account");
		System.out.println("  [2] Delete Account");
		System.out.println("  [3] Update Accounts");
		System.out.println("  [4] List All Accounts");
		System.out.println("  [5] Exit");
		System.out.println("---------------------------------------------------------");
	}
	
	private void createAccountCmd(Scanner scanner)
	{
		String username = "";
		String password = "";
		int accessLevel = 0; // Initialize access level (default 0 for User).
		
		while (username.isEmpty())
		{
			System.out.print("Enter username: ");
			final String line = readLine(scanner);
			if (line == null)
			{
				System.out.println("No input provided.");
				return;
			}

			username = line;
			if (username.isEmpty())
			{
				System.out.println("Username cannot be empty! Please provide a valid username.");
			}
		}
		
		while (password.isEmpty())
		{
			System.out.print("Enter password: ");
			final String line = readLine(scanner);
			if (line == null)
			{
				System.out.println("No input provided.");
				return;
			}

			password = line;
			if (password.isEmpty())
			{
				System.out.println("Password cannot be empty! Please provide a valid password.");
			}
		}
		
		// Display the access level options.
		System.out.println("Select access level from the following options:");
		System.out.println("0 - User (default)");
		System.out.println("-1 - Banned");
		System.out.println("10 - Chat Moderator");
		System.out.println("20 - Test GM");
		System.out.println("30 - General GM");
		System.out.println("40 - Support GM");
		System.out.println("50 - Event GM");
		System.out.println("60 - Head GM");
		System.out.println("70 - Admin");
		System.out.println("100 - Master");
		
		// Loop to get a valid access level.
		boolean validAccessLevel = false;
		while (!validAccessLevel)
		{
			System.out.print("Enter access level (default is 0 - User, or choose a number corresponding to the role): ");
			
			// Allow the user to press Enter without input (use default value of 0).
			final String input = readLine(scanner);
			if (input == null)
			{
				System.out.println("No input provided.");
				return;
			}

			if (input.isEmpty())
			{
				validAccessLevel = true;
				accessLevel = 0;
				System.out.println("Default access level (0 - User) selected.");
			}
			else
			{
				try
				{
					accessLevel = Integer.parseInt(input);
					
					// Validate if the entered access level is in the allowed list.
					if (VALID_ACCESS_LEVELS.contains(accessLevel))
					{
						validAccessLevel = true;
					}
					else
					{
						System.out.println("Invalid access level! Please select a valid role.");
					}
				}
				catch (NumberFormatException e)
				{
					System.out.println("Invalid input. Please enter a valid number for access level.");
				}
			}
		}
		
		try (Connection con = DatabaseFactory.getConnection())
		{
			if (con != null)
			{
				try (PreparedStatement statement = con.prepareStatement("SELECT COUNT(*) FROM accounts WHERE login = ?"))
				{
					statement.setString(1, username);
					try (ResultSet result = statement.executeQuery())
					{
						if (result.next() && (result.getInt(1) > 0))
						{
							System.out.println("Username already exists! Please choose another.");
							return;
						}
					}
				}
				
				final MessageDigest md = MessageDigest.getInstance("SHA");
				final byte[] hashedBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
				final String hashedPassword = Base64.getEncoder().encodeToString(hashedBytes);
				
				try (PreparedStatement statement = con.prepareStatement("INSERT INTO accounts (login, password, accessLevel) VALUES (?, ?, ?)"))
				{
					statement.setString(1, username);
					statement.setString(2, hashedPassword);
					statement.setInt(3, accessLevel);
					
					if (statement.executeUpdate() > 0)
					{
						System.out.println("Account " + username + " created successfully!");
					}
					else
					{
						System.out.println("Failed to create the account.");
					}
				}
			}
		}
		catch (Exception e)
		{
			System.out.println("Error creating account: " + e.getMessage());
		}
	}
	
	private void deleteAccountCmd(Scanner scanner)
	{
		System.out.print("Enter the username of the account to delete: ");
		String username = "";
		
		// Check if input is available.
		final String line = readLine(scanner);
		if (line == null)
		{
			System.out.println("No input provided.");
			return;
		}

		username = line;
		if (username.isEmpty())
		{
			System.out.println("Username cannot be empty!");
			return;
		}

		// Ask for confirmation before deleting the account.
		System.out.print("Are you sure you want to delete the account '" + username + "'? (y/n): ");
		final String confirmation = readLine(scanner);
		if ((confirmation == null) || !confirmation.equalsIgnoreCase("y"))
		{
			System.out.println("Account deletion cancelled.");
			return;
		}
		
		// Proceed with the account deletion logic.
		try (Connection con = DatabaseFactory.getConnection())
		{
			if (con != null)
			{
				try (PreparedStatement statement = con.prepareStatement("SELECT COUNT(*) FROM accounts WHERE login = ?"))
				{
					statement.setString(1, username);
					try (ResultSet result = statement.executeQuery())
					{
						if (result.next() && (result.getInt(1) == 0))
						{
							System.out.println("Account with the username '" + username + "' does not exist.");
							return;
						}
					}
				}
				
				try (PreparedStatement statement = con.prepareStatement("DELETE FROM accounts WHERE login = ?"))
				{
					statement.setString(1, username);
					
					if (statement.executeUpdate() > 0)
					{
						System.out.println("Account '" + username + "' deleted successfully.");
					}
					else
					{
						System.out.println("Failed to delete the account. Please try again.");
					}
				}
			}
		}
		catch (Exception e)
		{
			System.out.println("Error deleting account: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private void updateAccountCmd(Scanner scanner)
	{
		try
		{
			System.out.print("Enter the username of the account to update: ");
			final String username = readLine(scanner);
			if (username == null)
			{
				System.out.println("No input provided.");
				return;
			}

			if (username.isEmpty())
			{
				System.out.println("Username cannot be empty!");
				return;
			}

			try (Connection con = DatabaseFactory.getConnection())
			{
				if (con == null)
				{
					System.out.println("Database connection failed!");
					return;
				}
				
				// Check if account exists.
				try (PreparedStatement statement = con.prepareStatement("SELECT COUNT(*) FROM accounts WHERE login = ?"))
				{
					statement.setString(1, username);
					try (ResultSet result = statement.executeQuery())
					{
						if (result.next() && (result.getInt(1) == 0))
						{
							System.out.println("Account with username '" + username + "' does not exist.");
							return;
						}
					}
				}
				
				// Get new password (optional).
				System.out.print("Enter new password (or leave empty to keep current password): ");
				final String newPassword = readLine(scanner);
				if (newPassword == null)
				{
					System.out.println("No input provided.");
					return;
				}

				String hashBase64 = null;
				
				if (!newPassword.isEmpty())
				{
					final MessageDigest md = MessageDigest.getInstance("SHA");
					final byte[] raw = newPassword.getBytes(StandardCharsets.UTF_8);
					hashBase64 = Base64.getEncoder().encodeToString(md.digest(raw));
				}
				
				// Get new access level (optional).
				Integer newAccessLevel = null;
				while (true)
				{
					System.out.println("Select new access level (leave empty to keep current level):");
					System.out.println("0 - User");
					System.out.println("-1 - Banned");
					System.out.println("10 - Chat Moderator");
					System.out.println("20 - Test GM");
					System.out.println("30 - General GM");
					System.out.println("40 - Support GM");
					System.out.println("50 - Event GM");
					System.out.println("60 - Head GM");
					System.out.println("70 - Admin");
					System.out.println("100 - Master");
					
					System.out.print("Enter new access level: ");
					final String accessLevelInput = readLine(scanner);
					if (accessLevelInput == null)
					{
						System.out.println("No input provided.");
						return;
					}

					if (accessLevelInput.isEmpty())
					{
						break;
					}

					try
					{
						final int parsedLevel = Integer.parseInt(accessLevelInput);
						if (!VALID_ACCESS_LEVELS.contains(parsedLevel))
						{
							System.out.println("Invalid access level! Please select a valid role.");
							continue;
						}

						newAccessLevel = parsedLevel;
						break;
					}
					catch (NumberFormatException e)
					{
						System.out.println("Invalid access level. Please enter a valid number.");
					}
				}
				
				// If no changes, exit early.
				if ((hashBase64 == null) && (newAccessLevel == null))
				{
					System.out.println("No changes made to the '" + username + "' account.");
					return;
				}
				
				final StringBuilder sqlBuilder = new StringBuilder("UPDATE accounts SET ");
				final List<Object> params = new ArrayList<>();
				if (hashBase64 != null)
				{
					sqlBuilder.append("password = ?, ");
					params.add(hashBase64);
				}
				
				if (newAccessLevel != null)
				{
					sqlBuilder.append("accessLevel = ?, ");
					params.add(newAccessLevel);
				}
				
				sqlBuilder.setLength(sqlBuilder.length() - 2);
				sqlBuilder.append(" WHERE login = ?");
				params.add(username);
				
				try (PreparedStatement statement = con.prepareStatement(sqlBuilder.toString()))
				{
					for (int i = 0; i < params.size(); i++)
					{
						statement.setObject(i + 1, params.get(i));
					}
					
					final int rowsUpdated = statement.executeUpdate();
					if (rowsUpdated > 0)
					{
						System.out.println("Account '" + username + "' updated successfully!");
					}
					else
					{
						System.out.println("Failed to update the account. Please try again.");
					}
				}
			}
		}
		catch (Exception e)
		{
			System.out.println("Error updating account: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private void listAccountsCmd()
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			if (con != null)
			{
				try (PreparedStatement statement = con.prepareStatement("SELECT login, accessLevel FROM accounts");
					ResultSet result = statement.executeQuery())
				{
					if (!result.isBeforeFirst())
					{
						System.out.println("No accounts found.");
						return;
					}
					
					// Print the table header.
					System.out.println("================================================");
					System.out.printf("| %-4s | %-22s | %-12s |" + System.lineSeparator(), "No.", "Username", "Access Level");
					System.out.println("================================================");
					
					int totalAccounts = 0;
					while (result.next())
					{
						final String username = result.getString("login");
						final int accessLevel = result.getInt("accessLevel");
						System.out.printf("| %-4d | %-22s | %-12d |" + System.lineSeparator(), ++totalAccounts, username, accessLevel);
						System.out.println("------------------------------------------------");
					}

					System.out.println(System.lineSeparator() + "Total Accounts: " + totalAccounts);
				}
				catch (Exception e)
				{
					System.out.println("Error retrieving accounts: " + e.getMessage());
					e.printStackTrace();
				}
			}
		}
		catch (Exception e)
		{
			System.out.println("Error with database connection: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args)
	{
		DatabaseFactory.init();
		new AccountManager();
	}
}
