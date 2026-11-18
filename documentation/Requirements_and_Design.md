# Requirements and Design

## 1. Change History

| **Change Date**   | **Modified Sections** | **Rationale** |
| ----------------- | --------------------- | ------------- |
| Nov 18, 2026 | 3.1, 3.4, 4.1, 4.2 | Added the Notifications screen (issue #60). Join requests and their answers, invites, accepted friend requests and game start reminders are now saved as well as sent as a push, so they're listed on the Notifications tab, and tapping one, there or as a push, opens the screen to act on it. Added `GET /api/notifications`, `PATCH /api/notifications/read` and `notifyUser` / `notifyUsers` to the shared Notifications module, and a notifications table. Friend requests moved from the Friends tab to a Friend Requests screen opened from the top of Notifications, and the sender is no longer told when their request is declined. The organiser and players now get a reminder about 1 hour before a game starts. |
| Nov 17, 2026 | 3.4, 4.1 | Players can rate each other for 1 day after the organiser marks a game completed, so old games stop changing scores. The "Game completed" notification now asks players to rate their teammates, and players who still have someone to rate get a reminder 1 hour before rating closes. Games played now counts the completed games a player showed up to, from the same attendance the score uses, so the two always agree and a game deleted after everyone left still counts. |
| Nov 17, 2026 | 3.1, 3.4, 4.1 | Ratings are out of 6 stars. The reputation score no longer gives players a default of 5: a player with no ratings and no missed games is "Not rated yet" (a null score), and is left out of anything that ranks or filters by score, including badges. The score is now a weighted average of the player's games that starts from 5: each game scores the average over the other players who showed up, counting anyone who didn't rate as 5, newer games count more, unusual games count less, and each no-show counts as a game scoring 1. Section 4.1 gives the formula. |
| Nov 17, 2026 | 3.7, 4.7 | Following CP2 feedback, the Energy Efficiency requirement (at most 1% battery in 10 minutes) is replaced by Chat Responsiveness (a message reaches the other members within 1 second), because battery drain can't be checked by an automated test. Section 4.7 describes how chat meets it and how each requirement will be tested. |
| Nov 16, 2026 | 4.1, 4.3, 4.6, 4.7 | Following CP2 feedback, the components are renamed to match their code folders: Message is now Chat, Social is now Friend, and Player Review is now Review. Section 4.1 now matches the code: added complete game, leave game and delete account, remove player is built and only for the organiser, and `getReputation` returns the real score. Sending push notifications moved from User to a shared Notifications module, where the code has it. Section 4.3 adds the Google Places API, the Android Geocoder and Android Location, which the app uses for place search, addresses and the phone's location, and the Google Maps interface now matches the code. The planned `getFinishedGames` is removed, because rating starts from the finished game on the home screen. The sequence diagrams for Send Friend Message, Rate Players and Discover Game use the new component names and the REST replies the app receives. Section 4.7 says how the performance requirement will be tested. |
| Nov 16, 2026 | 3.1, 3.2, 3.3, 3.4, 3.5, 4.6 | Following CP2 feedback, use case names are now active verbs (e.g. View Player Statistics), and use cases with more than one goal are split: Add Friends into Search Users, Send, Accept and Decline Friend Request; Accept Players to Join into Accept and Reject Join Request; and each chat into send, view and receive messages. The game organiser now also takes part in game chat. The formal Join Request use case is renamed Join Game to match the list and diagram, and Friend Chat is renamed Send Friend Message. The failure scenarios of Create Game, Join Game and Rate Players now name each specific failure and the message shown, instead of "along with the reason". Features, use cases, formal use cases and sequence diagrams now all follow the same order as the use case diagram: Authentication, Friend System, Player Reputation and Statistics, Game Planning, Game Discovery. |
| Nov 10, 2026 | 4.1 | `GET /api/friends` now returns each friend's `chat_id`, so the app opens a friend's chat straight from the friends list (or a message notification) without asking for it first. Removed `GET /api/friends/{userId}/chat`. Whether the two are still friends is checked when the chat's messages load. |
| Nov 3, 2026 | 3 (Use Case 4), 4.1 | Friend chat is built on the same tables, WebSocket and screen as game chat. Added `GET /api/friends/{userId}/chat` and `createFriendChat`. Use Case 4 now starts from the Friends tab. |
| Nov 3, 2026 | 4.1 | Player ratings are built. Section 4.1 lists Player Review's real interfaces. No-shows are sent when the organiser marks a game completed, so there is no separate no-shows endpoint, and the score is computed when it's read instead of by `updateReputation`. |
| 2026-11-02 | 4.1 Main Components (User, Player Review, Social) | Built `GET /api/users/{userId}` for the player profile popup (Player Statistics use case). It returns a player's name, age range, games played, reputation score and badges, never their email or birth year. `getReputation` is built and counts games played; its score and badges stay empty until ratings are built. Added `getFriendshipStatus` so the popup knows when to offer Add Friend. |
| 2026-11-01 | 4.1 Main Components (Social) | Search results now include `friendship_status`, so the Friends tab shows Request Sent instead of Add once a request is sent. |
| 2026-11-01 | 4.1 Main Components (Social) | Built friend search, friend requests and the friends list for the new Friends tab. Added `GET /api/friends/requests` so the tab can list incoming requests, and search now also returns similar names so partial names and typos still find people. |
| Oct 31, 2026 | 4.1, 4.2, 4.4 | Game chat is built. Section 4.1 now lists the Message component's real interfaces, and a chat closes through the game's status instead of a separate `closeGameChat` call. Section 4.2 stores chat messages in MySQL instead of MongoDB, because the 1 GB server doesn't have the memory to run a second database. Section 4.4 removes the MongoDB driver and marks `ws` as in use. |
---

## 2. Project Description
SportsNow is a mobile app that helps people find like-minded players for sports games. Organisers can create games and set the sport, time, location, number of players, and the skill level they are looking for. Nearby players can browse these games and request to join. Users can also add friends, chat with them, and invite them to games.

To keep the community made up of accurately skilled players, SportsNow builds a reputation for every user. After each game, players rate one another on punctuality, sportsmanship, skill and reliability, and organisers record no-shows. These ratings feed a reputation score, and players also earn badges for milestones. Organisers see each requester’s score, history, and badges before accepting or rejecting them, so games are filled with reliable players.


---

## 3. Requirements Specification

### **3.1. List of Features**
1. Authentication: To access app features, a user must Sign In using Google Authentication Service first. New users must set up a profile by providing their first name, last name, and birth year. Users can also sign out.

2. Friend System: A user can search for other users and send, accept, or decline friend requests. Requests waiting for an answer are listed on a Friend Requests screen, opened from the top of the Notifications screen, and the sender is notified only when their request is accepted. A user can remove a friend. Friends can chat in real-time direct chat. A user can view a friend’s profile, badges, and upcoming games, and can invite them to games.

3. Player Reputation and Statistics: After each game, players rate each other on punctuality, sportsmanship, skill, and reliability, along with an overall rating, and the organiser marks players who did not show up. Our algorithm computes a reputation score out of 6, adjusting for the number of ratings, giving recent games more weight, counting unusual games less, and penalising no-shows. A player with no ratings shows “Not rated yet” instead of a score. The score updates after every game and is shown to organisers reviewing join requests. Milestones and badges are awarded automatically: “10 Games Completed”, “5 Games in One Week”, “Reliable Player”, “Always On Time”, and “Hosted 5 Games”. Badges are displayed on the user’s profile.

4. Game Planning: A user can create a game by specifying the sport, title, date, start time, max players, skill level, and an optional description. The user picks the game location on a map using the Google Maps API. Every player who wants to join must send a join request that the organiser approves. The organiser can then review join requests, each shown with the player’s rating, history, and badges, and can accept or reject each one. The join request list updates live as new requests arrive. The Game Organiser can also invite friends and edit or cancel the game. Each game has a temporary real-time chat room, which is closed after the game ends. The organiser and players get a reminder about 1 hour before the game starts. Join requests and their answers, invites and start reminders are sent as push notifications and also listed on a Notifications screen, where tapping one opens the screen to act on it, and a badge on the Notifications tab counts the unread ones and the friend requests waiting for an answer.

5. Game Discovery: A user can browse a list of games near their location, or near a place they choose on a map using the Google Maps API. Games are sorted by distance and can be filtered by sport. A user can view game details and the organiser’s reputation, and can send a join request with their skill level, how often they play, and an optional message. The user is notified when a request is accepted or rejected, and the game status updates automatically. A user can also view upcoming games and leave a game before it starts.


### **3.2. Use Case Diagram**
![Use Case Diagram](images/UCD_ver4.png)

### **3.3. Actors Description**
1. User: A user is anyone with a SportsNow account. A user signs up, logs in, signs out, manages friends, chats with friends, rates players, and views player statistics.
2. Player: A player is a user who joins games. A player discovers nearby games, requests to join them, and chats with other participants. A player builds a reputation and earns badges over time.
3. Game Organiser: A game organiser is a user who creates games. A game organiser creates and manages games, reviews join requests using each player’s stats, invites friends to a game, and chats with the players in their game.

### **3.4. Use Case Description**
#### - Use cases for Feature 1 Authentication: 
1. Log In: A returning user opens the app and signs in by verifying their identity with Google.
2. Log Out: A logged-in user signs out of their account and is returned to the first screen. 
3. Create Account: A new user opens the app and signs up. The user verifies their identity with Google, then provides their first name, last name, and birth year.

#### - Use cases for Feature 2 Friend System: 
4. Search Users: A user searches for other users by name.
5. Send Friend Request: A user sends a friend request to a user they found, and the other user is notified.
6. Accept Friend Request: A user opens the Friend Requests screen from the Notifications screen and accepts a friend request sent to them. The two users become friends and the sender is notified.
7. Decline Friend Request: A user opens the Friend Requests screen from the Notifications screen and declines a friend request sent to them. The sender is not notified.
8. Remove Friend: A user removes someone from their list of friends. 
9. Send Friend Message: A user selects a friend from their friend list, opens their chat, and sends them a message.
10. View Friend Messages: A user opens the chat with a friend and reads their earlier messages.
11. Receive Friend Message: A user receives a new message from a friend, live if the chat is open or as a push notification if it is not.
12. View Friend Profile: A user views a friend’s profile, including their badges and upcoming games.

#### - Use cases for Feature 3 Player Reputation and Statistics: 
13. Rate Players: After a game has finished, a user rates the other players. The user selects a player and rates them on punctuality, sportsmanship, skill, and reliability, along with an overall rating.
14. Mark No-shows: After a game has finished, the game organiser marks the players who did not show up.
15. View Player Statistics: A user views a player’s statistics, including their reputation score and the badges they have earned. 

#### - Use cases for Feature 4 Game Planning: 
16. Create Game: A game organiser creates a new game by entering its details and selecting a location on the map.
17. Edit Game: A game organiser changes the details of a game they created, such as the date, time, or location. 
18. Cancel Game: A game organiser cancels a game they created, and its players are notified.
19. Invite Friends: A game organiser invites one or more of their friends to a game they created. Each friend gets a notification, also listed on their Notifications screen, that opens the game.
20. Accept Join Request: A game organiser opens a join request for their game, from the game or from its notification, reviews it using the player’s rating, history, and badges, and accepts it, adding the player to the game. The player is notified.
21. Reject Join Request: A game organiser opens a join request for their game, from the game or from its notification, reviews it using the player’s rating, history, and badges, and rejects it. The player is notified.
22. Remove Player: A game organiser removes a player from a game they created. 

#### - Use cases for Feature 5 Game Discovery: 
23. Send Game Chat Message: A player who has joined a game, or its game organiser, sends a message to the other participants in the game’s temporary chat room, which is closed after the game ends.
24. View Game Chat Messages: A player who has joined a game, or its game organiser, opens its chat room and reads the earlier messages.
25. Receive Game Chat Message: A player or game organiser who has the game’s chat room open sees new messages from the other participants as they are sent.
26. Discover Game: A player finds games happening nearby. The system uses the player’s location, or a place they choose on the map, to show nearby games and their information, and the player can filter the list by sport.
27. Join Game: A player views the details of a game and sends a request to join it, stating their skill level, how often they play, and an optional message. 
28. Leave Game: A player who has joined a game withdraws from it before it starts. 
29. View Upcoming Games: A user views the upcoming games they organise and the games they have joined.


### **3.5. Formal Use Case Specifications (5 Most Major Use Cases)**
<a id="uc1r"></a>
#### **Use Case 1: Send Friend Message**

**Description**: A user selects a friend from their friend list, opens their chat, and sends them a message.

**Primary actor(s)**: User

**Preconditions**: User is logged in and has at least one friend

[**[Sequence Diagram]**](#seq-uc1)

**Main success scenario**:
1. User selects “Friends”.
2. System displays the list of the user’s friends.
3. User selects a friend.
4. System opens the chat with that friend.
5. User types a message and clicks “Send”.
6. System sends the message to the friend


**Failure scenario(s)**:
- 5a. User clicked “Send” without typing a message.
    - 5a1. System does not send anything and keeps the chat open.
- 6a. The message could not be sent (e.g. no internet connection).
    - 6a1. System displays a message saying the message could not be sent and lets the user try again.


<a id="uc2r"></a>
#### **Use Case 2: Rate Players**

**Description**: After a game has finished, a user rates the other players. The user selects a player and rates them on punctuality, sportsmanship, skill, and reliability, along with an overall rating.

**Primary actor(s)**: User

**Preconditions**: User is logged in, showed up to a game, and the organiser marked it completed less than 1 day ago
    
[**[Sequence Diagram]**](#seq-uc2)

**Main success scenario**:
1. User selects the finished game from the home menu.
2. System opens the game details.
3. User selects “Rate player”.
4. System displays all the players of the game.
5. User selects a player.
6. System displays the rating categories of punctuality, sportsmanship, skill, and reliability, each out of 6, as well as an overall rating.
7. User enters the ratings and clicks “Submit”.
8. System records the rating and displays a confirmation message.


**Failure scenario(s)**:
- 7a. User did not give a rating for one or more categories.
    - 7a1. System displays an error message identifying the missing ratings.
- 4a. More than 1 day has passed since the game was marked completed.
    - 4a1. System displays "Rating for this game has closed." and does not show the players.
- 8a. The User already rated this player for this game.
    - 8a1. System displays "You already rated this player" and does not record a second rating.
- 8b. The phone cannot reach the server (e.g. no internet connection).
    - 8b1. System displays "Couldn't reach the server, try again" and lets the User try again.
- 8c. The server fails while saving the rating.
    - 8c1. System displays "Couldn't submit your rating, try again" and lets the User try again.


<a id="uc3r"></a>
#### **Use Case 3: Create Game**

**Description**: A Game Organiser creates a new game by entering its details and selecting a location on the map.

**Primary actor(s)**: Game Organiser

**Preconditions**: Game Organiser is logged in

[**[Sequence Diagram]**](#seq-uc3)
    
**Main success scenario**:
1. Game Organiser selects “Create game”.
2. System displays the game creation form with fields for the sport, title, date, time, max players, skill level, description (optional), and a map for selecting the location.
3. Game Organiser fills in all the details and clicks on the map.
4. System opens the Map API.
5. Game Organiser selects the game location on the map.
6. System determines and displays the address of the selected location, then closes the map.
7. Game Organiser clicks “Create game”.
8. System creates the game and displays a confirmation message.


**Failure scenario(s)**:
- 4a. System cannot connect to the Map API.
    - 4a1. System displays an error message and lets the Game Organiser try again.
- 6a. The Map API cannot determine an address for the selected location.
    - 6a1. System displays an error message and asks the Game Organiser to select another location.
- 7a. Game Organiser left a compulsory field empty or did not select a location.
    - 7a1. System displays an error message identifying the missing fields.
- 7b. Game Organiser entered an invalid value in any field.
    - 7b1. System displays an error message identifying the invalid field and the reason.
- 8a. The phone cannot reach the server (e.g. no internet connection).
    - 8a1. System displays "Couldn't reach the server, try again" and lets the Game Organiser try again.
- 8b. The server fails while creating the game.
    - 8b1. System displays "Could not create game, try again" and lets the Game Organiser try again.


<a id="uc4r"></a>
#### **Use Case 4: Discover Game**

**Description**: A player finds games happening nearby. The system uses the player’s location, or a place they choose on the map, to show nearby games and their information, and the player can filter the list by sport.

**Primary actor(s)**: Player 

**Preconditions**: Player is logged in

[**[Sequence Diagram]**](#seq-uc4)

**Main success scenario**: 
1. Player selects the compass in the navigation bar 
2. System gets the Player’s location from the phone.
3. System displays the nearby games sorted by distance with their information, and a selector bar at the top with various sports.
4. Player selects a sport in the selector bar.
5. System updates the list to show only the games of the selected sport.


**Failure scenario(s)**:
- 2a. System cannot get the Player’s location (e.g. location is turned off or location access is denied).
    - 2a1. System displays an error message and lets the Player try again or choose a place on the map.
- 3a. No games are found nearby.
    - 3a1. System displays a message saying no nearby games were found.
- 5a. No nearby games are found for the selected sport.
    - 5a1. System displays a message saying no games were found of the selected sport.


<a id="uc5r"></a>
#### **Use Case 5: Join Game**

**Description**: A player sends a request to join a game, stating their skill level, how often they play, and an optional message. The request waits for the organiser’s approval.

**Primary actor(s)**: Player

**Preconditions**: Player is logged in and is viewing the details of a game they have not joined

[**[Sequence Diagram]**](#seq-uc5)
    
**Main success scenario**:
1. Player selects “Request to join”.
2. System displays the join request form with fields for skill level, how often they play (optional), and a message (optional).
3. Player fills in the form and clicks “Send request”.
4. System sends the request to the organiser and shows the request as pending.



**Failure scenario(s)**:
- 3a. Player did not select a skill level.
    -  3a1. System displays an error message asking the Player to select their skill level.
- 4a. The organiser cancelled the game before the request was sent.
    - 4a1. System displays "This game was cancelled" and does not send the request.
- 4b. The game started before the request was sent.
    - 4b1. System displays "This game has already started" and does not send the request.
- 4c. The phone cannot reach the server (e.g. no internet connection).
    - 4c1. System displays "Couldn't reach the server, try again" and lets the Player try again.



### **3.6. Screen Mock-ups**


### **3.7. Non-Functional Requirements**
<a id="nfr1"></a>

1. **Performance**
    - **Description**: Discover Game should finish loading and display the nearby games in under 5 seconds from when the user selects the compass.
    - **Justification**: In Nielsen’s Response Times: The 3 Important Limits, about 10 seconds is the limit for keeping the user’s attention focused, and delays between 1 and 10 seconds make users feel at the mercy of the computer and explore less freely. Within that range, real user monitoring data from Gomez, cited by Countey (2015), shows that websites loading within 5 seconds keep abandonment to about 20%. This figure is for websites, where a visitor can leave at no cost. SportsNow users have chosen to install the app, so they are more committed and should abandon even less at the same delay. A 5-second target therefore sits well inside Nielsen’s 10-second limit and in the range where abandonment is low. 

<a id="nfr2"></a>

2. **Chat Responsiveness**
    - **Description**: When a user sends a message in a game chat or friend chat, the other members who have the chat open should receive it within 1 second.
    - **Justification**: In Nielsen’s Response Times: The 3 Important Limits, about 1 second is the limit for the user’s flow of thought to stay uninterrupted, even though they notice the delay. Chat is a back-and-forth conversation, often used to arrange a game that is about to start, such as who is bringing the ball or where to meet. If a reply takes longer than 1 second to appear, the conversation stops feeling live and users start wondering whether their message was sent. A 1-second target keeps chat within Nielsen’s limit for an uninterrupted flow.


---

## 4. Designs Specification
### **4.1. Main Components**
All front-end to back-end interfaces are HTTP/REST calls that send and return JSON. Every endpoint except sign up and login needs the JWT from sign up or login in the `Authorization: Bearer <token>` header. Back-end components call each other directly in the same Node.js process, and those interfaces are written as Java-style method signatures.

1. **User**
    - **Purpose**: Handles sign up, login, sign out, and user profiles, verifying identity through Google Authentication.
    - **Rationale**: Keeping accounts in their own component lets every other component get the signed in user from one place. We chose Google sign in with our own JWT over username and password accounts so we never store passwords.
    - **Interfaces**: 
        1. `POST /api/auth/signup` body `{ id_token, email, f_name, l_name, birthYear }` → `201 { msg: "signedIn", jwt_token }`
            - **Purpose**: Verifies the Google ID token, creates the user's account, and returns a JWT for later requests.
        2. `POST /api/auth/login` body `{ id_token }` → `200 { msg: "signedIn", jwt_token }` or `404 { msg: "no_user" }`
            - **Purpose**: Verifies the Google ID token and returns a JWT if the user already has an account.
        3. `POST /api/register_notif` body `{ fcm_token }` → `200 { msg: "success" }`
            - **Purpose**: Saves the device's Firebase token so the server can send the user push notifications.
        4. `GET /api/users/me` → `200 { user }`
            - **Purpose**: Returns the signed in user's profile.
        5. `PUT /api/users/me` body `{ f_name, l_name, birthYear }` → `200 { user }`
            - **Purpose**: Updates the signed in user's profile.
        6. `DELETE /api/users/me` → `200 { msg: "success" }`
            - **Purpose**: Deletes the signed in user's account and everything tied to it. Players of games they organise that are still on are told the game was cancelled.
        7. `User findUserById(int userId)`
            - **Purpose**: Used by the other components to get a user's name and details, returning null if no user has the id.
        8. `GET /api/users/{userId}` → `200 { profile }`
            - **Purpose**: Returns another player's public profile for the profile popup: name, age range, games played, reputation score and badges, and a `friendship_status` (`self` on the user's own profile) so the app knows whether to offer Add Friend. Never returns email or birth year.

2. **Game**
    - **Purpose**: Handles creating, editing, cancelling, completing, discovering, joining, and leaving games, accepting and removing players, and invitations, using the Google Maps API for locations.
    - **Rationale**: Games, their players and join requests change together, so one component owns them and can lock a game while it changes, so two approvals at once can't go over the player limit. We chose to search by distance in a MySQL query over loading every game into the server so Discover Game stays fast.
    - **Interfaces**: 
        1. `POST /api/games` body `{ title, sport, description, starts_at, ends_at, location_name, address, latitude, longitude, max_players, skill_level }` → `201 { game }`
            - **Purpose**: Creates a game with the signed in user as its organiser and first player.
        2. `GET /api/games/mine` → `200 { organized_games, joined_games }`
            - **Purpose**: Returns the games the user organises and the games they have joined.
        3. `GET /api/games/discover?lat=&lng=` → `200 { games }` or `200 { msg: "no_games" }`
            - **Purpose**: Returns the nearby games the user can join, closest first.
        4. `GET /api/games/{gameId}` → `200 { game, organizer, players, viewer_role, chat_id }`
            - **Purpose**: Returns a game's details, its players, and what the signed in user can do with it.
        5. `PUT /api/games/{gameId}` body same as create → `200 { game }`
            - **Purpose**: Lets the organiser edit the details of their game. Moving the start time clears its start reminder, so the players are reminded again before the new time.
        6. `POST /api/games/{gameId}/cancel` → `200 { game }`
            - **Purpose**: Lets the organiser cancel their game and notifies its players.
        7. `POST /api/games/{gameId}/join-requests` body `{ skill_level, play_frequency, message }` → `201 { request }`
            - **Purpose**: Sends a join request for a game and notifies the organiser with `notifyUser`. Tapping the notification opens the game's join requests.
        8. `GET /api/games/{gameId}/join-requests` → `200 { game, requests }`
            - **Purpose**: Returns the pending join requests for the organiser to review.
        9. `PATCH /api/games/{gameId}/join-requests/{requestId}` body `{ decision: "approve" | "decline" }` → `200 { game }`
            - **Purpose**: Lets the organiser approve or decline a join request and notifies the requester with `notifyUser` either way.
        10. `POST /api/games/{gameId}/complete` body `{ no_show_user_ids }` (optional) → `200 { game }`
            - **Purpose**: Lets the organiser mark a game that has started as completed, recording the players who didn't show up and when it was completed, and notifies its players to rate each other within 1 day. Fails with `game_not_started` before the start time. Each game the app receives carries `rating_closes_at`, 1 day after it was completed, so it only offers Rate Players while rating is open.
        11. `POST /api/games/{gameId}/leave` → `200 { game, game_deleted }`
            - **Purpose**: Lets a player leave a game and tells the organiser if the game is still on. The organiser can't leave a game that is still on and must cancel or complete it instead (`organizer_must_cancel`, `organizer_must_complete`). The last player to leave a cancelled or completed game deletes it, and `game_deleted` is then true.
        12. `DELETE /api/games/{gameId}/players/{userId}` → `200 { game }`
            - **Purpose**: Lets the organiser remove a player from their game before it starts, and notifies the player, who can send a new join request later.
        13. `POST /api/games/{gameId}/invites` body `{ friend_ids }` → `201 { msg: "success" }`
            - **Purpose**: Lets the organiser invite friends to their game by notifying them with `notifyUsers`, so the invite is also on their Notifications screen. Tapping the notification opens the game, where the friend can send a join request. Only the organiser can invite, only their friends who aren't in the game yet, and not once the game is cancelled, started or full.
        14. `List<GamePlayer> getGamePlayers(int gameId)`
            - **Purpose**: Used by Review and Chat to check who played in a game.
    - **Start reminder**: every minute the server looks for scheduled games starting within 1 hour that haven't had their reminder (`start_reminder_sent`), marks each as reminded in the same transaction so it's only reminded once, and sends the organiser and players a "Game starting soon" notification with `notifyUsers`, e.g. "Sunday Soccer starts in 1 hour". A game created or moved to start within the hour is reminded on the next check with the minutes left. Cancelled and completed games aren't reminded. Tapping it opens the game.

3. **Review**
    - **Purpose**: Handles player ratings after a game, no-shows, reputation scores, and badges.
    - **Rationale**: The reputation score and badges are computed from ratings, so keeping them apart from games keeps the scoring algorithm in one place. We chose to compute the score when it's read over storing it, because players can still rate a game after it's completed.
    - **Reputation score**: worked out in `review/reputation.ts`, with no database code so the rules are in one place.
        - **Game scores**: each game a player showed up to scores the average over every other player who showed up, where a rating is the average of its five categories (1 to 6 stars) and anyone who didn't rate them counts as 5. So a game nobody rated, or nobody else showed up to, scores 5. Averaging makes the order the ratings were given in not matter, and every game counts the same however many players were in it. A game they didn't show up to scores 1. Games are ordered newest first by when they were played, and only the 50 newest are read, since older ones barely change the score.
        - **Not rated yet**: a player nobody has rated, who never missed a game, has no score (`null`), even if they played games. The app shows "Not rated yet", and anything that ranks or filters players by score, including score-based badges, must leave them out instead of treating them as 5.
        - **Recency**: the newest game has weight 1, and each older one has 0.9 times the weight of the one after it, so every new game makes all the older ones count 10% less: wᵢ = 0.9ⁱ for the game with i newer games.
        - **Unusual games**: once a player showed up to at least 4 games, a game scoring more than 1.5 stars from the median of their game scores is multiplied by 0.25. With fewer games there isn't enough to tell what is unusual. No-shows are never counted less.
        - **Few games**: the score is a Bayesian average that starts from 3 games scoring 5, so a few games can't make it extreme. One game rated 6 by its only other player gives 5.25, ten give 5.68, one game rated 1 and 6 by two players gives 4.63, and a game where one of three other players gave 6 gives 5.08:

          score = (3 × 5 + Σ wᵢ × gameScoreᵢ) / (3 + Σ wᵢ), rounded to two decimal places.
        - **Rating window**: players can rate each other for 1 day after the organiser marks the game completed (`completed_at`), after which both rating interfaces refuse with `rating_closed`. Every minute the server looks for completed games whose rating closes within 1 hour, marks each as reminded so it's only reminded once, and sends a "Rating closes soon" notification to the players who showed up and still haven't rated someone else who did. Tapping it opens the game.
        - **Abuse**: a player can rate another only once per game (the table's primary key), only if both showed up to that completed game, only within the rating window, and never themselves. A rating can't be changed once given. A game where someone was rated far lower or higher than usual only moves their score a quarter as much.
    - **Interfaces**:
        1. `GET /api/games/{gameId}/ratings` → `200 { game_title, organizer_id, players }`
            - **Purpose**: Returns the other players who showed up to a completed game and whether the user already rated them. Fails with `rating_closed` once the rating window has ended.
        2. `POST /api/games/{gameId}/ratings` body `{ rated_user_id, punctuality, sportsmanship, skill, reliability, overall }` → `201 { msg: "success" }`
            - **Purpose**: Records a user's rating of another player after a finished game. A player can only be rated once per game, and only within 1 day of the game being marked completed (`rating_closed` after that).
        3. `GET /api/users/me/stats` → `200 { reputation_score }` (`null` if not rated yet)
            - **Purpose**: Returns the signed in user's own score for their profile.
        4. `Double getReputationScore(int userId)`
            - **Purpose**: Used by Game to show each requester's score on the join request list. Returns null if the player isn't rated yet.
        5. `Reputation getReputation(int userId)`
            - **Purpose**: Used by User's public profile to show a player's reputation score and games played. Games played counts the completed games they showed up to, from the same attendance the score uses. Its badges stay empty until badges are built.
        6. `GET /api/users/{userId}/stats` → `200 { reputation_score, games_played, badges }` *(planned)*
            - **Purpose**: Returns a user's reputation score, game history, and badges.

4. **Chat**
    - **Purpose**: Handles chat messages for both game chat and friend chat.
    - **Rationale**: Game chat and friend chat send and store messages the same way, so one component handles both. We chose a WebSocket connection over polling so messages arrive live and the app doesn't use battery asking for new ones.
    - **Interfaces**: Game chat and friend chat are built and share these interfaces. A chat's members are its game's current players, or the two friends.
        1. `GET /api/chats/{chatId}/messages?before=` → `200 { messages, is_closed }`
            - **Purpose**: Returns 50 of the chat's earlier messages, newest first, to a member of the chat. `before` is the oldest message id the app already has. `is_closed` is true once the game is completed or cancelled.
        2. WebSocket `send_message { chat_id, text, client_id }` → `new_message { message, client_id }`, or `error { msg, error, client_id }`
            - **Purpose**: Sends a message and delivers it live to everyone in the chat. The connection needs `?chat_id=` and the user's JWT, and only opens for a member of the chat. A friend who doesn't have the chat open gets a push notification. Sending fails with `chat_closed` once the game is completed or cancelled, so the chat can still be read but not written to. `client_id` lets the app match a reply to the message it sent.
        3. `int createGameChat(Queryable connection, int gameId)`
            - **Purpose**: Used by Game to open the chat room for a new game in the same transaction that creates the game, returning its chat id.
        4. `void createFriendChat(Queryable connection, int userId, int otherUserId)`
            - **Purpose**: Used by Friend to open two users' chat room in the same transaction that makes them friends.

5. **Friend**
    - **Purpose**: Handles friend search, friend requests, removing friends, and viewing friends' profiles.
    - **Rationale**: Friendships are used by friend chat, game invites and friend profiles, so one component owns them. We chose to keep it apart from User so the User component only handles accounts and profiles.
    - **Interfaces**: Search, friend requests, the friends list, `areFriends` and `getFriendshipStatus` are built. Removing friends and friend profiles are *(planned)*.
        1. `GET /api/users/search?q=` → `200 { users }`
            - **Purpose**: Searches for users by name as the user types, returning at most 20 with the best match first. Names that start with or contain `q` come first, then similar names, so partial names and typos still find people. Each user has a `friendship_status` of `none`, `friends`, `request_sent` or `request_received`, so the app can show Add or where the request stands.
        2. `POST /api/friends/requests` body `{ user_id }` → `201 { request }`
            - **Purpose**: Sends a friend request and notifies the other user. Blocked for yourself, a user who doesn't exist, someone who is already a friend, and when either user already has a pending request to the other.
        3. `GET /api/friends/requests` → `200 { requests }`
            - **Purpose**: Returns the requests sent to the user that are waiting for an answer, newest first.
        4. `PATCH /api/friends/requests/{requestId}` body `{ decision: "accept" | "decline" }` → `200 { msg: "success" }`
            - **Purpose**: Accepts or declines a friend request. Accepting makes the two users friends and notifies the sender with `notifyUser`. Declining notifies nobody, so no one is told they were turned down.
        5. `GET /api/friends` → `200 { friends }`
            - **Purpose**: Returns the user's friends, each with `chat_id`, the chat the two share, so the app opens it straight from the list.
        6. `DELETE /api/friends/{userId}` → `200 { msg: "success" }` *(planned)*
            - **Purpose**: Removes a friend.
        7. `GET /api/friends/{userId}` → `200 { user, badges, upcoming_games }` *(planned)*
            - **Purpose**: Returns a friend's profile with their badges and upcoming games.
        8. `boolean areFriends(int userId, int otherUserId)`
            - **Purpose**: Used by Game to check that two users are friends before an invite.
        9. `String getFriendshipStatus(int userId, int otherUserId)`
            - **Purpose**: Used by User's public profile to say whether the two users are friends or one has a pending request to the other, so the popup shows Add Friend or where the request stands.

**Shared module: Notifications** (`backend/src/core/notifications` and `backend/src/notification`)

Notifications are sent by several components, so sending them lives in a shared module instead of in one component. It also stores each user's device tokens, which `POST /api/register_notif` saves, and the notifications listed on the app's Notifications screen. A push is best effort, so the ones a user should still see after dismissing it are saved too: a friend request accepted, a new join request, a join request approved or declined, a game invite, and a game starting soon. New friend requests aren't saved, they're answered on the Friend Requests screen, and neither are chat messages, which have their own screen.
- `int notifyUser(int userId, NewNotification notification)` and `int notifyUsers(List<Integer> userIds, NewNotification notification)`
    - **Purpose**: Used by Game and Friend to save a notification (`type`, `title`, `body`, and the game it's about if any) for each user's Notifications screen and send it as a push, returning how many devices it reached. The push carries the type and game id, so tapping it opens the same screen as tapping the saved one. If saving fails the push is still sent.
- `int sendPushToUser(int userId, PushMessage message)`
    - **Purpose**: Used by Game and Friend to send a push notification that isn't saved, such as a new friend request, to every device one user is signed into, returning how many devices it reached.
- `int sendPushToUsers(List<Integer> userIds, PushMessage message)`
    - **Purpose**: Used by Game, Chat, User and Review to notify several users at once without saving it, such as a game's players when it is cancelled, a chat's members about a new message, players when an account is deleted, or players who still have teammates to rate, returning how many devices it reached.
- `GET /api/notifications` → `200 { notifications, unread_count, friend_request_count }`
    - **Purpose**: Returns the signed in user's 50 newest notifications, newest first, each with `notification_id`, `type`, `title`, `body`, `game_id`, `created_at` and `is_read`. `unread_count` and `friend_request_count` (requests waiting for an answer) make up the badge on the Notifications tab, and `friend_request_count` is shown on the Friend requests button.
- `PATCH /api/notifications/read` → `200 { msg: "success" }`
    - **Purpose**: Marks all the user's notifications as seen, once the Notifications screen showed them.

On the app, tapping a notification, on the Notifications screen or as a push, opens where the user can act on it: a new friend request opens Friend Requests, an accepted one the Friends tab, a join request the organiser's join requests for that game, and anything else about a game opens the game.


### **4.2. Databases**
1. **Relational Database (MySQL)**
    - **Purpose**: Stores users, games, players, join requests, device tokens, chats and their messages, friend requests, friendships, ratings, and the notifications listed on the Notifications screen.
    - **Rationale**: This data has clear links between tables and needs transactions, such as locking a game while a join request is approved. We chose MySQL over a document database for these joins and transactions. Chat messages are stored here too, instead of in a second database, because the 1 GB server doesn't have the memory to run both, and checking who can read a chat already needs the game and player tables.



### **4.3. External Modules**
1. **Google Authentication** 
    - **Purpose**: Verifies users’ identity when they sign up and log in.
    - **Rationale**: We chose it over our own password accounts so we don't store passwords, and most Android users already have a Google account.
    - **Interface**: `OAuth2Client.verifyIdToken(String idToken, String audience)` → `LoginTicket` holding the Google account id and verified email.
2. **Google Maps API** 
    - **Purpose**: Provides the map for selecting a game location and for choosing a place to search for nearby games.
    - **Rationale**: We chose it over OpenStreetMap because it is made for Android, is well documented, and uses the same Google Cloud project as Google sign in.
    - **Interface**: `GoogleMap(CameraPositionState cameraPositionState, MapProperties properties, MapUiSettings uiSettings)` from Maps Compose, which shows the map in the app. A fixed pin marks the map's centre, and `cameraPositionState` gives the coordinates under it once the user stops moving the map.
3. **Google Places API** 
    - **Purpose**: Lets the user search for a place by name when picking a game location or a place to discover games near, and returns its name, address and coordinates.
    - **Rationale**: We chose it because it uses the same Google Cloud project and API key as the Maps SDK, and typing a place name is quicker than finding it on the map.
    - **Interface**: `Task<FindAutocompletePredictionsResponse> PlacesClient.findAutocompletePredictions(FindAutocompletePredictionsRequest request)`, which returns place suggestions for the typed text, and `Task<FetchPlaceResponse> PlacesClient.fetchPlace(FetchPlaceRequest request)`, which returns the chosen place's details.
4. **Android Geocoder** 
    - **Purpose**: Turns the spot the user picks on the map into a place name and address.
    - **Rationale**: We chose it over the Google Geocoding web API because it is built into Android and needs no extra API key or billing.
    - **Interface**: `List<Address> Geocoder.getFromLocation(double latitude, double longitude, int maxResults)`, which returns the addresses nearest a point.
5. **Android Location (LocationManager)** 
    - **Purpose**: Gives the phone's current location once when Discover Game opens, so the app can search for games nearby.
    - **Rationale**: We chose it over Google Play services' fused location client because it is built into Android and needs no extra library, and Android 12+ still offers a fused provider through it.
    - **Interface**: `Location LocationManager.getLastKnownLocation(String provider)`, which returns the last location a provider such as fused, network or GPS found, or null if there is none. If that location is too old, the app asks for a fresh one with `LocationManagerCompat.getCurrentLocation(...)`.
6. **Firebase Cloud Messaging** 
    - **Purpose**: Sends push notifications, such as new join requests, friend requests and their decisions, to users' phones.
    - **Rationale**: We chose it over keeping a connection open or polling the server because it is built into Android and saves battery.
    - **Interface**: `BatchResponse messaging.sendEachForMulticast(MulticastMessage message)`, which sends a title, body and data to a list of device tokens and reports which tokens no longer work.


### **4.4. Frameworks and Libraries**
#### Back-end
1. **Node.js with TypeScript**
    - **Purpose**: Runs the back-end server.
    - **Reason**: TypeScript's types catch mistakes in request and database fields before the code runs, and Node.js handles many requests at once without threads.
2. **Express.js**
    - **Purpose**: Routes HTTP requests to the handlers for each component and parses JSON bodies.
    - **Reason**: It is small, well known, and lets us split the routes into one router per component.
3. **mysql2**
    - **Purpose**: Connects to the MySQL database and runs queries and transactions.
    - **Reason**: It supports connection pools, transactions and parameterised queries, which keep queries safe from SQL injection.
4. **google-auth-library**
    - **Purpose**: Verifies the Google ID token the app sends when signing up or logging in.
    - **Reason**: It is Google's own library, so it checks the token's signature and audience correctly.
5. **jsonwebtoken**
    - **Purpose**: Creates and checks the JWT the app sends with every request after signing in.
    - **Reason**: A JWT lets the server know who the user is without storing sessions.
6. **firebase-admin**
    - **Purpose**: Sends push notifications to users' phones through Firebase Cloud Messaging.
    - **Reason**: It is the official way for a server to send Firebase notifications and reports tokens that no longer work.
7. **ws**
    - **Purpose**: Keeps a WebSocket connection open while a chat screen is shown so messages arrive live.
    - **Reason**: It is a small WebSocket library that works with the Express server.
8. **dotenv**
    - **Purpose**: Loads settings such as the database password and Google client id from a `.env` file.
    - **Reason**: It keeps secrets out of the code.
9. **Jest and Supertest**
    - **Purpose**: Run the back-end tests and send test HTTP requests to the server.
    - **Reason**: Jest is the most used Node.js test framework, and Supertest calls the Express app without starting a real server.

#### Front-end
1. **Kotlin with Jetpack Compose (Material 3)**
    - **Purpose**: Builds the Android app and its screens.
    - **Reason**: Compose is Android's recommended UI toolkit, and building screens in Kotlin code is quicker than XML layouts.
2. **Navigation Compose**
    - **Purpose**: Moves between the app's screens.
    - **Reason**: It is the standard navigation library for Compose apps.
3. **Retrofit with Gson and OkHttp**
    - **Purpose**: Makes the HTTP requests to the back-end and turns the JSON replies into Kotlin objects. OkHttp also keeps the chat's WebSocket connection open.
    - **Reason**: Retrofit turns each endpoint into one Kotlin function, which keeps the network code short.
4. **Credential Manager with Google ID**
    - **Purpose**: Shows the Google sign in prompt and gets the Google ID token.
    - **Reason**: It is Android's current recommended way to sign in with Google.
5. **Firebase Cloud Messaging**
    - **Purpose**: Gets the device's notification token and receives push notifications.
    - **Reason**: It is built into Android and works with the firebase-admin library on the back-end.
6. **Maps Compose, Play Services Maps and Places**
    - **Purpose**: Show the map for picking a game location and discovering games, and search for places by name.
    - **Reason**: Maps Compose lets us use Google Maps directly in Compose screens.


### **4.5. Dependencies Diagram**
![Dependencies Diagram](images/Dependency%20diagram-High%20Level%20Diagram.jpg)


### **4.6. Use Case Sequence Diagram (5 Most Major Use Cases)**
1. <a id="seq-uc1"></a> [**Use Case 1: Send Friend Message**](#uc1r)\
![Sequence Diagram UC1](images/UC1%20-%20Send%20Friend%20Message_ver5.png)
<br><br>

2. <a id="seq-uc2"></a> [**Use Case 2: Rate Players**](#uc2r)\
![Sequence Diagram UC2](images/UC2%20-%20Rate%20Players_ver5.png)
<br><br>

3. <a id="seq-uc3"></a> [**Use Case 3: Create Game**](#uc3r)\
![Sequence Diagram UC3](images/UC3%20-%20Create%20Game_ver4.png)
<br><br>

4. <a id="seq-uc4"></a> [**Use Case 4: Discover Game**](#uc4r)\
![Sequence Diagram UC4](images/UC4%20-%20Discover%20Game_ver5.png)
<br><br>

5. <a id="seq-uc5"></a> [**Use Case 5: Join Game**](#uc5r)\
![Sequence Diagram UC5](images/UC5%20-%20Join%20Game_ver4.png)
<br><br>

### **4.7. Design of Non-Functional Requirements**
1. [**Performance**](#nfr1)
    - **Implementation**: The front-end sends a single request to the Game component, which gets the nearby games with one MySQL query that filters by distance, sorts them closest first, and returns a limited number of games. The front-end shows a loading indicator while it waits, and filters the list by sport on the phone without sending another request.
    - **Verification**: A Jest test seeds 1,000 games around a point and checks that `GET /api/games/discover` answers in under 1 second, returning at most 50 games, closest first.
2. [**Chat Responsiveness**](#nfr2)
    - **Implementation**: While a chat screen is open, the app keeps a WebSocket connection to the Chat component, so a message is pushed to the other members as soon as it is sent, without them polling for it. When a message arrives, the Chat component checks that the sender can still write to the chat, saves the message with a single MySQL insert, and immediately sends it to every open connection for that chat. The push notification for a friend who doesn’t have the chat open is sent afterwards, so it never delays delivery to the members who do.
    - **Verification**: A Jest test connects two members of a chat over WebSocket, sends 20 messages from one, and checks that the other receives each `new_message` within 1 second of it being sent.
