
// First destination for incoming clients

import java.io.*;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class ServerAuthenticator extends Thread{
	
    private Socket clientSocket;
	private BufferedReader fromClient;
	private PrintStream toClient;
	private ClientTable clientTable;
	private PasswordTable passwordTable;
	private String firstInput = null;
	private String commands = 
			"Commands:\n"
			+ "     login \n"
			+ "     register \n"
			+ "     quit \n\n";
	
	ServerAuthenticator(BufferedReader f, PrintStream to, ClientTable t, PasswordTable p, Socket so) {
		fromClient = f;
		toClient = to;
		clientTable = t;
		passwordTable = p;
		clientSocket = so;
	}

    public void run() {
        String user = null;

        try {
            toClient.println("Welcome to the messaging service. \n");
            // Currently this Thread shouldn't ever be interrupted
            while (!Thread.interrupted()) {
                toClient.println(commands);
                firstInput = Server.getInput(fromClient.readLine());
                switch (firstInput.toLowerCase()) {
                case "login":
                    user = attemptLogin();
                    if (user != null) {
                        Report.behaviour("Server Authenticator: " + user + " has logged on.");
                        exitWith(user);
                        return;
                    }
                    break;
                case "register":
                    user = attemptRegister();
                    if (user != null) {
                        Report.behaviour("Server Authenticator: " + user + " has registered.");
                        exitWith(user);
                        return;
                    }
                    break;
                default:
                    toClient.println("Unrecognised Input. Try Again.");
                    break;
                }

            }
        } catch (IOException e) {
            Report.error("ServerAuthenticator: IOException: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            Report.error("ServerAuthenticator: NoSuchAlgorithmException: " + e.getMessage());
        } catch (InvalidKeySpecException e) {
            Report.error("ServerAuthenticator: InvalidKeySpecException: " + e.getMessage());
        } catch (ClientHasQuitException e) {
            // Client has decided to quit. Close streams and exit.
            Report.behaviour("ServerAuthenticator: Client quiting before logging in");
        }

        // Attempt to close socket and streams
        // Note: not using finally block as we don't want
        // to close streams on a successful login attempt
        toClient.close();
        try {
            fromClient.close();
        } catch (IOException e) {
            // Nothing to do
        }

        try {
            clientSocket.close();
        } catch (IOException e) {
            // Nothing to do
        }

        // exit thread
    }

    private String attemptLogin()
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, ClientHasQuitException {
        String username;
        String givenPassword;

        toClient.println("Username: ");
        username = Server.getInput(fromClient.readLine());

        PasswordEntry correct = passwordTable.getPasswordEntry(username);
        if (correct == null) {
            toClient.println("No such username. Please try again or register.");
            return null;
        }

        toClient.println("Password: ");
        givenPassword = Server.getInput(fromClient.readLine());

        byte[] encryptedP = correct.getPassword();
        byte[] salt = correct.getSalt();
        if (!PasswordService.authenticate(givenPassword, encryptedP, salt)) {
            toClient.println("Incorrect Password. Please try again or register.");
            return null;
        }

        // User authorised, return name of authorised user
        return username;
    }

    private String attemptRegister()
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, ClientHasQuitException {
        String username;
        String desiredPassword;

        toClient.println("Desired username: ");
        username = Server.getInput(fromClient.readLine());

        if (username.length() < Server.MIN_USERNAME_LENGTH) {
            toClient.println("Username too short. Please try again.");
            return null;
        }
        if (passwordTable.isInTable(username)) {
            toClient.println("Username already taken. Please try again.");
            return null;
        }

        toClient.println("Desired password: ");
        desiredPassword = Server.getInput(fromClient.readLine());

        if (desiredPassword.length() < Server.MIN_PASSWORD_LENGTH) {
            toClient.println(
                    "Invalid password. Password must be at least " + Server.MIN_PASSWORD_LENGTH + " chars long.");
            return null;
        }

        toClient.println("Please confirm password: ");

        if (desiredPassword.equals(Server.getInput(fromClient.readLine()))) {
            byte[] salt = PasswordService.generateSalt();
            byte[] encryptedPassword = PasswordService.encrypt(desiredPassword, salt);
            passwordTable.add(username, new PasswordEntry(salt, encryptedPassword));
            toClient.println("Account successfully created.");
            return username;
        }
        toClient.println("Passwords did not match. Please try again.");
        return null;
    }

    private void exitWith(String user) {
        String newUser = user;
        clientTable.add(newUser);

        ServerSender serverSend = new ServerSender(clientTable.getQueue(newUser), toClient);
        serverSend.start();

        // We create and start a new thread to read from the client:
        (new ServerReceiver(newUser, fromClient, toClient, clientTable, passwordTable, serverSend, clientSocket))
                .start();

    }

}