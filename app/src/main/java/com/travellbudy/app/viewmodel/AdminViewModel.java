package com.travellbudy.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.travellbudy.app.models.Announcement;
import com.travellbudy.app.models.Category;
import com.travellbudy.app.models.Trip;
import com.travellbudy.app.models.User;
import com.travellbudy.app.repository.AdminRepository;

import java.util.List;

/**
 * ViewModel for AdminPanelActivity and its fragments.
 * Provides data for user management, content moderation, and dashboard.
 */
public class AdminViewModel extends ViewModel {

    private final AdminRepository adminRepository;

    private LiveData<Boolean> isAdmin;
    private LiveData<List<User>> allUsers;
    private LiveData<List<Trip>> allTrips;
    private LiveData<List<Category>> allCategories;
    private LiveData<List<Announcement>> allAnnouncements;
    private LiveData<AdminRepository.DashboardStats> dashboardStats;
    private LiveData<List<String>> featuredTripIds;

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();

    public AdminViewModel() {
        adminRepository = AdminRepository.getInstance();
    }

    // =========================================================================
    // Admin verification
    // =========================================================================

    public LiveData<Boolean> isCurrentUserAdmin() {
        if (isAdmin == null) {
            isAdmin = adminRepository.isCurrentUserAdmin();
        }
        return isAdmin;
    }

    // =========================================================================
    // User Management
    // =========================================================================

    public LiveData<List<User>> getAllUsers() {
        if (allUsers == null) {
            allUsers = adminRepository.getAllUsers();
        }
        return allUsers;
    }

    public void banUser(String uid) {
        isLoading.setValue(true);
        adminRepository.banUser(uid)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Потребителят е блокиран");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при блокиране: " + e.getMessage());
            });
    }

    public void unbanUser(String uid) {
        isLoading.setValue(true);
        adminRepository.unbanUser(uid)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Потребителят е отблокиран");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при отблокиране: " + e.getMessage());
            });
    }

    public void promoteToAdmin(String uid) {
        isLoading.setValue(true);
        adminRepository.promoteToAdmin(uid)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Потребителят е повишен до администратор");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при повишаване: " + e.getMessage());
            });
    }

    public void demoteToUser(String uid) {
        isLoading.setValue(true);
        adminRepository.demoteToUser(uid)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Администраторът е понижен");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при понижаване: " + e.getMessage());
            });
    }

    public void deleteUser(String uid) {
        isLoading.setValue(true);
        adminRepository.deleteUser(uid)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Потребителят е изтрит");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при изтриване: " + e.getMessage());
            });
    }

    // =========================================================================
    // Content Moderation
    // =========================================================================

    public LiveData<List<Trip>> getAllTrips() {
        if (allTrips == null) {
            allTrips = adminRepository.getAllTrips();
        }
        return allTrips;
    }

    public LiveData<List<String>> getFeaturedTripIds() {
        if (featuredTripIds == null) {
            featuredTripIds = adminRepository.getFeaturedTripIds();
        }
        return featuredTripIds;
    }

    public void deleteTrip(String tripId) {
        isLoading.setValue(true);
        adminRepository.deleteTrip(tripId)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Пътуването е изтрито");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при изтриване: " + e.getMessage());
            });
    }

    public void featureTrip(String tripId) {
        isLoading.setValue(true);
        adminRepository.featureTrip(tripId)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Пътуването е отбелязано като препоръчано");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка: " + e.getMessage());
            });
    }

    public void unfeatureTrip(String tripId) {
        isLoading.setValue(true);
        adminRepository.unfeatureTrip(tripId)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Пътуването е премахнато от препоръчани");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка: " + e.getMessage());
            });
    }

    // =========================================================================
    // Category Management
    // =========================================================================

    public LiveData<List<Category>> getAllCategories() {
        if (allCategories == null) {
            allCategories = adminRepository.getAllCategories();
        }
        return allCategories;
    }

    public void createCategory(Category category) {
        isLoading.setValue(true);
        adminRepository.createCategory(category)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Категорията е създадена");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при създаване: " + e.getMessage());
            });
    }

    public void updateCategory(Category category) {
        isLoading.setValue(true);
        adminRepository.updateCategory(category)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Категорията е обновена");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при обновяване: " + e.getMessage());
            });
    }

    public void deleteCategory(String categoryId) {
        isLoading.setValue(true);
        adminRepository.deleteCategory(categoryId)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Категорията е изтрита");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при изтриване: " + e.getMessage());
            });
    }

    // =========================================================================
    // Announcements
    // =========================================================================

    public LiveData<List<Announcement>> getAllAnnouncements() {
        if (allAnnouncements == null) {
            allAnnouncements = adminRepository.getAllAnnouncements();
        }
        return allAnnouncements;
    }

    public void createAnnouncement(String title, String message, String type, long expiresAt) {
        isLoading.setValue(true);
        adminRepository.createAnnouncement(title, message, type, expiresAt)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Съобщението е създадено");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при създаване: " + e.getMessage());
            });
    }

    public void deactivateAnnouncement(String announcementId) {
        isLoading.setValue(true);
        adminRepository.deactivateAnnouncement(announcementId)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Съобщението е деактивирано");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка: " + e.getMessage());
            });
    }

    public void deleteAnnouncement(String announcementId) {
        isLoading.setValue(true);
        adminRepository.deleteAnnouncement(announcementId)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Съобщението е изтрито");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при изтриване: " + e.getMessage());
            });
    }

    // =========================================================================
    // Push Notifications
    // =========================================================================

    public void sendPushNotificationToAll(String title, String message) {
        isLoading.setValue(true);
        adminRepository.sendPushNotificationToAll(title, message)
            .addOnSuccessListener(aVoid -> {
                isLoading.setValue(false);
                successMessage.setValue("Известието е изпратено до всички потребители");
            })
            .addOnFailureListener(e -> {
                isLoading.setValue(false);
                errorMessage.setValue("Грешка при изпращане: " + e.getMessage());
            });
    }

    // =========================================================================
    // Dashboard
    // =========================================================================

    public LiveData<AdminRepository.DashboardStats> getDashboardStats() {
        if (dashboardStats == null) {
            dashboardStats = adminRepository.getDashboardStats();
        }
        return dashboardStats;
    }

    public void refreshDashboardStats() {
        adminRepository.refreshDashboardStats();
    }

    // =========================================================================
    // UI State
    // =========================================================================

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getSuccessMessage() {
        return successMessage;
    }

    public void clearMessages() {
        errorMessage.setValue(null);
        successMessage.setValue(null);
    }
}

