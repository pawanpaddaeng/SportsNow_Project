# Requirements and Design

## 1. Change History

| **Change Date**   | **Modified Sections** | **Rationale** |
| ----------------- | --------------------- | ------------- |
| _Nothing to show_ |

---

## 2. Project Description
SportsNow is a mobile app that helps people find like-minded players for sports games. Organisers can create games and set the sport, time, location, and the skill level, age group, and gender they are looking for. Nearby players can browse these games and request to join. Users can also add friends, chat with them, and invite them to games.

To keep the community made up of accurately skilled players, SportsNow builds a reputation for every user. After each game, players rate one another on punctuality, sportsmanship, skill and reliability, and organisers record no-shows. These ratings feed a reputation score, and players also earn badges for milestones. Organisers see each requester’s score, history, and badges before accepting or rejecting them, so games are filled with reliable players.


---

## 3. Requirements Specification

### **3.1. List of Features**
1. Authentication: To access app features, a user must Sign In using Google Authentication Service first. New users must set up a profile by providing their name, age, gender, sports, and skill level per sport. Users can also sign out.

2. Game Planning: A user can create a game by specifying the sport (from the database), date, time, duration, max players, age group, gender, and skill level. The user picks the game location on a map using the Google Maps API. The organiser can then review join requests, each shown with the player’s rating, history, and badges, and can accept or reject each one. The join request list updates live as new requests arrive. The Game Organiser can also invite friends and edit or cancel the game. Each game has a temporary real-time chat room, which is closed after the game ends.

3. Game Discovery: A user can browse nearby games as a list or on a map using the Google Maps API. Games are sorted by distance, and the games are filtered by sport, time, skill level, age group, and gender. A user can view game details and the organiser’s reputation, and can send a join request with their skill level. The user is notified when a request is accepted or rejected, and the game status updates automatically. A user can also view upcoming games and leave a game before it starts.

4. Friend System: A user can search for other users and send, accept, or decline friend requests. A user can remove a friend. Friends can chat in real-time direct chat. A user can view a friend’s profile, badges, and upcoming games, and can invite them to games. Friend recommendations are based on shared games, mutual friends, sports, and skill level.

5. Player Reputation and Statistics: After each game, players rate each other on punctuality, sportsmanship, skill honesty, and reliability, and the organiser marks players who did not show up. Our algorithm computes a reputation score, weighting recent games more heavily, adjusting for the number of ratings, and penalising no-shows and late cancellations. The score updates after every game and is shown to organisers reviewing join requests. Milestones and badges are awarded automatically: “10 Games Completed”, “5 Games in One Week”, “Reliable Player”, “Always On Time”, and “Hosted 5 Games”. Badges are displayed on the user’s profile.


### **3.2. Use Case Diagram**
![Use Case Diagram](images/Use%20Case%20Diagram.png)

### **3.3. Actors Description**
1. User: A user is anyone with a SportsNow account. A user signs up, logs in, signs out, manages friends, chats with friends, rates players, and views player statistics.
2. Player: A player is a user who joins games. A player discovers nearby games, requests to join them, and chats with other participants. A player builds a reputation and earns badges over time.
3. Game Organiser: A game organiser is a user who creates games. A game organiser creates and manages games, reviews join requests using each player’s stats, and invites friends to a game.

### **3.4. Use Case Description**
- Use cases for Feature 1 Authentication: 
1. Login: A returning user opens the app and signs in by verifying their identity with Google.
2. Sign out: A logged-in user signs out of their account and is returned to the first screen. 
3. Account Creation: A new user opens the app and signs up. The user verifies their identity with Google, then provides their personal details and, optionally, their favourite sports, skill level, and a photo.

- Use cases for Feature 2 Game Planning: 
4. Create Game: A game organiser creates a new game by entering its details, choosing whether to require approval for join requests, and selecting a location on the map.
5. Edit Game: A game organiser changes the details of a game they created, such as the date, time, or location. 
6. Accept players to join: A game organiser reviews the join requests for their game, using each player’s rating, history, and badges, and accepts or rejects each one. 
7. Kick people from Group: A game organiser removes a player from a game they created. 
8. Invite Friend: A user invites one or more of their friends to a game they created. 
9. Game Chat: Players who have joined a game chat with the other participants in a temporary chat room, which is closed after the game ends. 

- Use cases for Feature 3 Game Discovery: 
10. Discover Game: A player finds games happening nearby. The system uses the player’s location to show nearby games and their information, and the player can filter the list by sport.
11. Join Game: A player views the details of a game and sends a request to join it, stating their skill level. 
12. Leave Game: A player who has joined a game withdraws from it before it starts. 

- Use cases for Feature 4 Friend System: 
13. Add Friend: A user searches for another user and sends them a friend request, which the other user can accept or decline. 
14. Remove Friend: A user removes someone from their list of friends. 
15. Friend Chat: A user chats with one of their friends. The user selects a friend from their friend list, opens the chat, and sends a message.

- Use cases for Feature 5 Player Reputation and Statistics: 
16. Rate Players: After a game has finished, a user rates the other players. The user selects a player and rates them on sportsmanship, skill, honesty, and reliability, along with an overall rating.
17. Player Statistics: A user views their player’s statistics, including their reputation score and the badges they have earned. 



### **3.5. Formal Use Case Specifications (5 Most Major Use Cases)**
<a name="uc1"></a>
#### Use Case 1: Account Creation

**Description**: A new user opens the app and signs up. The user verifies their identity with Google, then provides their personal details and, optionally, their favourite sports, skill level, and a photo.

**Primary actor(s)**: User

**Preconditions**: None
    
**Main success scenario**:
1. User selects “Sign up”.
2. System displays a pop-up to verify with Google.
3. User verifies with Google.
4. System asks the user for their first name, last name, and birth year, and optionally their favourite sports, skill level, and a photo.
5. User enters their details and submits.
6. System registers the user’s account and logs the user into the new account


**Failure scenario(s)**:
- 2a. Google verification cannot be reached.
    -  2a1. System displays an error message and returns to the first screen so the user can try again.
- 5a. User entered illegal characters in at least one field.
    -  5a1. System displays an error message identifying the erroneous field and characters.
- 5b. User did not enter text in the name fields and/or birth year.
    -  5b1. System displays an error message saying “First Name, Last Name and Birth Year fields are compulsory.”
- 6a. The account could not be registered.
    -  6a1. System displays a message saying the account could not be registered, along with the reason.


<a name="uc2"></a>
#### Use Case 2: Create Game

**Description**: A Game Organiser creates a new game by entering its details, choosing whether to require approval for join requests, and selecting a location on the map.

**Primary actor(s)**: Game Organiser

**Preconditions**: Game Organiser is logged in
    
**Main success scenario**:
1. Game Organiser selects “Create game”.
2. System displays the game creation form with fields for the game name, date, time, player count, skill level, description (optional), an “Approve join request” toggle, and a map for selecting the location.
3. Game Organiser fills in all the details, sets the toggle, and clicks on the map.
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
- 8a. The game could not be created.
    - 8a1. System displays a message saying the game could not be created, along with the reason.


<a name="uc3"></a>
#### Use Case 3: Discover Game

**Description**: A player finds games happening nearby. The system uses the player’s location to show nearby games and their information, and the player can filter the list by sport.


**Primary actor(s)**: Player 

**Preconditions**: Player is logged in
    
**Main success scenario**: 
1. Player selects the compass in the navigation bar 
2. System uses the Map API to get the Player’s location.
3. System displays the nearby games with their information, and a selector bar at the top with various sports.
4. Player selects a sport in the selector bar.
5. System updates the list to show only the games of the selected sport.


**Failure scenario(s)**:
- 2a. System cannot get the Player’s location (e.g. the Map API cannot be reached or location access is denied).
    - 2a1. System displays an error message and lets the Player try again.
- 3a. No games are found nearby.
    - 3a1. System displays a message saying no nearby games were found.
- 5a. No nearby games are found for the selected sport.
    - 5a1. System displays a message saying no games were found of the selected sport.


<a name="uc4"></a>
#### Use Case 4: Friend Chat

**Description**:  A user chats with one of their friends. The user selects a friend from their friend list, opens the chat, and sends a message.

**Primary actor(s)**: User

**Preconditions**: User is logged in and has at least one friend
    
**Main success scenario**:
1. User selects “Chat”.
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


<a name="uc5"></a>
#### Use Case 5: Rate Players

**Description**: After a game has finished, a user rates the other players. The user selects a player and rates them on sportsmanship, skill, honesty, and reliability, along with an overall rating.

**Primary actor(s)**: User

**Preconditions**: User is logged in and has finished a game
    
**Main success scenario**:
1. User selects the finished game from the home menu.
2. System opens the game details.
3. User selects “Rate player”.
4. System displays all the players of the game.
5. User selects a player.
6. System displays the rating categories of sportsmanship, skill, honesty, and reliability, each out of 5, as well as an overall rating.
7. User enters the ratings and clicks “Submit”.
8. System records the rating and displays a confirmation message.


**Failure scenario(s)**:
- 7a. User did not give a rating for one or more categories.
    - 7a1. System displays an error message identifying the missing ratings.
- 8a. The rating could not be submitted (e.g. no internet connection).
    - 8a1. System displays a message saying the rating could not be submitted along with a reason for the failure



### **3.6. Screen Mock-ups**


### **3.7. Non-Functional Requirements**
<a name="nfr1"></a>

1. **Performance**
    - **Description**: Discover Game should finish loading and display the nearby games in under 5 seconds from when the user selects the compass.
    - **Justification**: In Nielsen’s Response Times: The 3 Important Limits, about 10 seconds is the limit for keeping the user’s attention focused, and delays between 1 and 10 seconds make users feel at the mercy of the computer and explore less freely. Within that range, real user monitoring data from Gomez, cited by Countey (2015), shows that websites loading within 5 seconds keep abandonment to about 20%. This figure is for websites, where a visitor can leave at no cost. SportsNow users have chosen to install the app, so they are more committed and should abandon even less at the same delay. A 5-second target therefore sits well inside Nielsen’s 10-second limit and in the range where abandonment is low. 

2. **Energy Efficiency**
    - **Description**: Using the app for 10 minutes should reduce the phone’s battery level by no more than 1%.
    - **Justification**: People spend an average of 4 hours and 37 minutes on their phones each day. If an average user drains about 80% of their battery over that time, the average phone uses roughly 17% of its battery per hour of use (80% ÷ 4.62 hours). SportsNow is not designed to be as energy intensive as typical apps such as video, games, or social media, so its target is about one-third of that rate, or 6% per hour. A drain of 1% per 10 minutes meets this target and helps avoid battery concerns, which Ickin et al. (2017) found to be one of the top reasons users uninstall apps. 


---

## 4. Designs Specification
### **4.1. Main Components**
1. **User**
    - **Purpose**: Handles sign up, login, sign out, and user profiles, verifying identity through Google Authentication.
    - **Interfaces**: 
        1. ...
            - **Purpose**: ...
        2. ...

2. **Game**
    - **Purpose**: Handles creating, editing, discovering, joining, and leaving games, accepting and removing players, and invitations, using the Google Maps API for locations.
    - **Interfaces**: 
        1. ...
            - **Purpose**: ...
        2. ...

3. **Player Review**
    - **Purpose**: Handles player ratings after a game, no-shows, reputation scores, and badges.
    - **Interfaces**: 
        1. ...
            - **Purpose**: ...
        2. ...

4. **Message**
    - **Purpose**: Handles chat messages for both game chat and friend chat.
    - **Interfaces**: 
        1. ...
            - **Purpose**: ...
        2. ...

5. **Social**
    - **Purpose**: Handles friend search, friend requests, removing friends, and friend recommendations.
    - **Interfaces**: 
        1. ...
            - **Purpose**: ...
        2. ...


### **4.2. Databases**
1. **Relational Database (MySQL)**
    - **Purpose**: ...
2. **Chat Database (MongoDB)**
    - **Purpose**: Stores chat messages for the Message component.



### **4.3. External Modules**
1. **Google Authentication** 
    - **Purpose**: Verifies users’ identity when they sign up and log in.
2. **Google Maps API** 
    - **Purpose**: Provides the map for selecting a game location and determines addresses, and gives the location for nearby game search.


### **4.4. Frameworks and Libraries**
1. **[WRITE_NAME_HERE]**
    - **Purpose**: ...
    - **Reason**: ...
2. ...


### **4.5. Dependencies Diagram**



### **4.6. Use Case Sequence Diagram (5 Most Major Use Cases)**
1. [**[WRITE_NAME_HERE]**](#uc1)\
[SEQUENCE_DIAGRAM_HERE]
2. ...


### **4.7. Design of Non-Functional Requirements**
1. [**[WRITE_NAME_HERE]**](#nfr1)
    - **Implementation**: ...
2. ...
