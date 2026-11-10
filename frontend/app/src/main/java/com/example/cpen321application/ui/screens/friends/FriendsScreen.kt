package com.example.cpen321application.ui.screens.friends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cpen321application.friend.model.FriendPerson
import com.example.cpen321application.friend.model.FriendRequestItem
import com.example.cpen321application.friend.model.FriendshipStatus
import com.example.cpen321application.ui.components.AppBottomBar
import com.example.cpen321application.ui.components.BottomTab
import com.example.cpen321application.ui.components.CardRow
import com.example.cpen321application.ui.components.ListNote
import com.example.cpen321application.ui.components.cardBackground
import com.example.cpen321application.ui.components.loadStatus
import com.example.cpen321application.ui.components.pillBackground
import com.example.cpen321application.ui.components.sportsFieldColors
import com.example.cpen321application.ui.theme.*

/*  what the Friends screen shows. A non blank query shows searchResults instead of the
    requests and friends. isLoading shows a loading bar and errorMessage a problem.
    busyRequestIds and busyUserIds are answers and requests being sent, their buttons are disabled
 */
data class FriendsUiState(
    val query: String = "",
    val friends: List<FriendPerson> = emptyList(),
    val requests: List<FriendRequestItem> = emptyList(),
    val searchResults: List<FriendPerson> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val busyRequestIds: Set<Int> = emptySet(),
    val busyUserIds: Set<Int> = emptySet()
)

// what the Friends screen's controls do
data class FriendsActions(
    val onQueryChange: (String) -> Unit = {},
    val onRetry: (() -> Unit)? = null,
    val onAddFriend: (FriendPerson) -> Unit = {},
    val onAccept: (FriendRequestItem) -> Unit = {},
    val onDecline: (FriendRequestItem) -> Unit = {},
    val onOpenProfile: (Int) -> Unit = {},
    val onOpenChat: (FriendPerson) -> Unit = {},
    val onTabSelected: (BottomTab) -> Unit = {}
)

/*
    Friends tab, in the style of the Figma mockups: search at the top to find people and
    send them requests, then requests waiting for an answer, then accepted friends.
    The actions connect it to other screens through AppNavigation
 */
@Composable
fun FriendsScreen(state: FriendsUiState, actions: FriendsActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AppBottomBar(selectedTab = BottomTab.Friends, onTabSelected = actions.onTabSelected)
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item(key = "title") {
                Text(text = "Friends", color = SportsOnSurface, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
            item(key = "search") { SearchField(query = state.query, onQueryChange = actions.onQueryChange) }

            loadStatus(isLoading = state.isLoading, errorMessage = state.errorMessage, onRetry = actions.onRetry)

            if (state.query.isBlank()) {
                requestsAndFriends(state, actions)
            } else {
                searchResults(state, actions)
            }
        }
    }
}

private fun LazyListScope.searchResults(state: FriendsUiState, actions: FriendsActions) {
    items(state.searchResults, key = { "result${it.userId}" }) { person ->
        PersonCard(person = person) {
            when (person.status) {
                FriendshipStatus.None ->
                    AddButton(isBusy = person.userId in state.busyUserIds, onClick = { actions.onAddFriend(person) })
                FriendshipStatus.Friends -> StatusPill("Friends")
                FriendshipStatus.RequestSent -> StatusPill("Request Sent")
                FriendshipStatus.RequestReceived -> StatusPill("Sent you a request")
            }
        }
    }
    if (state.searchResults.isEmpty() && !state.isLoading && state.errorMessage == null) {
        item(key = "noResults") { ListNote("No one found with that name.") }
    }
}

private fun LazyListScope.requestsAndFriends(state: FriendsUiState, actions: FriendsActions) {
    if (state.requests.isNotEmpty()) {
        item(key = "requestsHeader") { SectionHeader(title = "Friend Requests", count = state.requests.size) }
        items(state.requests, key = { "request${it.requestId}" }) { request ->
            RequestCard(
                request = request,
                isBusy = request.requestId in state.busyRequestIds,
                onAccept = { actions.onAccept(request) },
                onDecline = { actions.onDecline(request) }
            )
        }
    }

    item(key = "friendsHeader") { SectionHeader(title = "Your Friends", count = state.friends.size) }
    items(state.friends, key = { "friend${it.userId}" }) { friend ->
        PersonCard(
            person = friend,
            onClick = { actions.onOpenChat(friend) },
            onAvatarClick = { actions.onOpenProfile(friend.userId) }
        )
    }
    if (state.friends.isEmpty() && !state.isLoading && state.errorMessage == null) {
        item(key = "noFriends") { ListNote("No friends yet. Search for someone by name to send them a request.") }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Search by name") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SportsOnSurfaceVariant) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = SportsOnSurfaceVariant)
                }
            }
        },
        shape = RoundedCornerShape(50),
        colors = sportsFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

// section title with its count in a small pill, as on Home
@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text(text = title, color = SportsOnSurface, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(
            text = count.toString(),
            color = SportsOnSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.pillBackground(SportsSurfaceContainer, borderColor = null)
        )
    }
}

// with onClick the circle can be tapped to open the person's profile
@Composable
private fun Avatar(initial: String, onClick: (() -> Unit)? = null) {
    val tapToOpen = onClick?.let { Modifier.clickable(onClickLabel = "View profile", onClick = it) } ?: Modifier
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SportsPrimaryContainer)
            .then(tapToOpen)
    ) {
        Text(text = initial, color = SportsOnPrimaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/*  one person: initial and name in a card like Home's game cards, trailing goes on the right.
    With onClick the card can be tapped to open the chat with a friend, and with onAvatarClick
    the circle to open their profile instead
 */
@Composable
private fun PersonCard(
    person: FriendPerson,
    onClick: (() -> Unit)? = null,
    onAvatarClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val tapToOpen = onClick?.let { Modifier.clickable(onClickLabel = "Open chat", onClick = it) } ?: Modifier
    CardRow(
        modifier = Modifier
            .cardBackground(SportsSurfaceContainer)
            .border(1.dp, SportsOutlineVariant, RoundedCornerShape(12.dp))
            .then(tapToOpen)
    ) {
        Avatar(person.initial, onClick = onAvatarClick)
        Text(
            text = person.name,
            color = SportsOnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

// where a search result stands instead of the Add button, like Discover's "Request pending"
@Composable
private fun StatusPill(text: String) {
    Text(
        text = text,
        color = SportsPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.pillBackground(Color.White, borderColor = SportsPrimary, horizontalPadding = 10.dp)
    )
}

@Composable
private fun AddButton(isBusy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isBusy,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "Add", fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// a request: who sent it, then Decline and Accept, like the Join Requests cards
@Composable
private fun RequestCard(request: FriendRequestItem, isBusy: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, SportsOutlineVariant, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(request.sender.initial)
            Text(text = request.sender.name, color = SportsOnSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onDecline,
                enabled = !isBusy,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, SportsOutline),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = SportsPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Decline", color = SportsPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onAccept,
                enabled = !isBusy,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = SportsPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Accept", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
