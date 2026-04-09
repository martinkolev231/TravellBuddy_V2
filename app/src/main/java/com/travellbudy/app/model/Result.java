package com.travellbudy.app.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Generic wrapper class for representing the state of an asynchronous operation.
 * Used by all Repositories and ViewModels to expose success/error/loading states
 * via LiveData.
 *
 * <p>Usage pattern:
 * <pre>
 *   // In Repository:
 *   liveData.setValue(Result.loading());
 *   liveData.setValue(Result.success(data));
 *   liveData.setValue(Result.error("Something failed"));
 *
 *   // In Fragment:
 *   viewModel.getData().observe(this, result -> {
 *       switch (result.status) {
 *           case LOADING: showSpinner(); break;
 *           case SUCCESS: showData(result.data); break;
 *           case ERROR:   showError(result.message); break;
 *       }
 *   });
 * </pre>
 *
 * @param <T> The type of data held in a successful result.
 */
public class Result<T> {

    public enum Status {
        SUCCESS,
        ERROR,
        LOADING
    }

    @NonNull
    public final Status status;

    @Nullable
    public final T data;

    @Nullable
    public final String message;

    private Result(@NonNull Status status, @Nullable T data, @Nullable String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    /**
     * Creates a successful result containing data.
     */
    @NonNull
    public static <T> Result<T> success(@Nullable T data) {
        return new Result<>(Status.SUCCESS, data, null);
    }

    /**
     * Creates an error result with a user-friendly message.
     */
    @NonNull
    public static <T> Result<T> error(@NonNull String message) {
        return new Result<>(Status.ERROR, null, message);
    }

    /**
     * Creates an error result that also preserves stale data for offline display.
     */
    @NonNull
    public static <T> Result<T> error(@NonNull String message, @Nullable T staleData) {
        return new Result<>(Status.ERROR, staleData, message);
    }

    /**
     * Creates a loading result (operation in progress).
     */
    @NonNull
    public static <T> Result<T> loading() {
        return new Result<>(Status.LOADING, null, null);
    }

    /**
     * @return true if this result represents a successful operation.
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * @return true if this result represents an error.
     */
    public boolean isError() {
        return status == Status.ERROR;
    }

    /**
     * @return true if this result represents a loading state.
     */
    public boolean isLoading() {
        return status == Status.LOADING;
    }
}

