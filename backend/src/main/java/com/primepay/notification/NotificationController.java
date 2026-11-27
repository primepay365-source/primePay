package com.primepay.notification;

import com.primepay.common.api.ApiResponse;
import com.primepay.common.api.PageResponse;
import com.primepay.security.CurrentActor;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final CurrentActor currentActor;

    public NotificationController(NotificationService notifications, CurrentActor currentActor) {
        this.notifications = notifications;
        this.currentActor = currentActor;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationRow>> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Long me = currentActor.require().getId();
        var result = notifications.list(me, category, unread, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return ApiResponse.ok(PageResponse.from(result, NotificationRow::from));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.ok(Map.of("unread", notifications.unreadCount(currentActor.require().getId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notifications.markRead(currentActor.require().getId(), id);
        return ApiResponse.ok(null, "Marked as read");
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        return ApiResponse.ok(Map.of("updated", notifications.markAllRead(currentActor.require().getId())));
    }

    public record NotificationRow(Long id, String category, String title, String body, String link,
                                  boolean read, OffsetDateTime createdAt) {
        static NotificationRow from(Notification n) {
            return new NotificationRow(n.getId(), n.getCategory(), n.getTitle(), n.getBody(), n.getLink(), n.getReadAt() != null, n.getCreatedAt());
        }
    }
}
