# ChatLite — Minimal Real-Time Chat System

## How to Compile

Open a terminal in the project root and run:

```bash
javac server/*.java
javac client/*.java
```

## How to Run

**Start the Server first:**
```bash
java server.ChatServer
```
The Server Console GUI will open. It starts listening on TCP port 5000 automatically.

**Start one or more Clients:**
```bash
java client.ClientGUI
```
Enter the server IP (use `localhost` if running on the same machine), port `5000`, and valid credentials.

## Sample Users (users.txt)

Place this file in the same directory you run the server from:

```
ahmed:1234
sara:abcd
mohammed:pass1
student1:test99
```

## Sample Rooms

Three rooms are created automatically on server start:
- General
- Networks
- Java

## Protocol Summary

| Command              | Server Response     |
|----------------------|---------------------|
| HELLO user pass      | 200 WELCOME / 401   |
| JOIN room            | 210 JOINED          |
| MSG room message     | 211 SENT            |
| PM user message      | 212 PRIVATE SENT    |
| USERS                | 213 list… 213 END   |
| ROOMS                | 214 room…           |
| LEAVE room           | 215 LEFT            |
| QUIT                 | 221 BYE             |
