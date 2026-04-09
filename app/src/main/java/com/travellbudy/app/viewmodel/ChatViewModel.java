package com.travellbudy.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.travellbudy.app.model.Result;
import com.travellbudy.app.models.ChatMessage;
import com.travellbudy.app.repository.ChatRepository;

import java.util.List;

/**
 * ViewModel for the Chat screen (message thread).
 * Observes messages via ChildEventListener (realtime), provides sendMessage action.
 * Removes listener in onCleared().
 */
public class ChatViewModel extends AndroidViewModel {

    private final ChatRepository chatRepository;
    private LiveData<Result<List<ChatMessage>>> messagesLiveData;
    private String chatId;

    public ChatViewModel(@NonNull Application application) {
        super(application);
        this.chatRepository = new ChatRepository(application);
    }

    /**
     * Starts observing messages for the given chat.
     */
    public void loadMessages(@NonNull String chatId) {
        this.chatId = chatId;
        messagesLiveData = chatRepository.observeMessages(chatId);
    }

    public LiveData<Result<List<ChatMessage>>> getMessages() {
        return messagesLiveData;
    }

    /**
     * Sends a message in the current chat.
     */
    public LiveData<Result<Void>> sendMessage(@NonNull ChatMessage message,
                                                @NonNull String senderUid,
                                                @NonNull String recipientUid,
                                                @NonNull String senderName,
                                                @NonNull String recipientName,
                                                String tripId) {
        return chatRepository.sendMessage(chatId, message,
                senderUid, recipientUid, senderName, recipientName, tripId);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        chatRepository.removeListeners();
    }
}

