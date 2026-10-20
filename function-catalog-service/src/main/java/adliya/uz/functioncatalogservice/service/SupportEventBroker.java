package adliya.uz.functioncatalogservice.service;

import adliya.uz.functioncatalogservice.config.HelpProperties;
import adliya.uz.functioncatalogservice.exception.RateLimitExceededException;
import adliya.uz.functioncatalogservice.security.OrganizationScope;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Near-real-time notices for the support chat over server-sent events.
 *
 * <p>An event says only that something changed ({@code MESSAGE}, {@code READ}, {@code STATUS}) and in which message or
 * conversation; it never carries message text or contact details. Clients reload the thread or inbox through the
 * regular, authorized REST endpoints, so persisted messages and unread state stay the source of truth and a missed
 * event is repaired by the next reload (clients also reload after every reconnect). Event ids are unique per service
 * instance ({@code <boot>-<sequence>}) so a client can drop duplicates.</p>
 *
 * <p>Visitors receive events of their own support identity only; staff receive events of conversations inside the
 * organization scope they had when the stream was opened (central conversations: global staff only). Streams end after
 * {@link HelpProperties.Stream#getTimeout()} and are authorized again on reconnect. Subscriptions live in memory: with
 * several catalog instances an event reaches only the clients connected to the instance that handled the change, and
 * the clients' fallback polling covers the rest.</p>
 */
@Component
@Slf4j
public class SupportEventBroker {

    public enum Type { MESSAGE, READ, STATUS }

    private record Subscriber(Long guestId, Long sessionId, Long userId, OrganizationScope scope) {
        boolean staff() {
            return scope != null;
        }
    }

    private final HelpProperties.Stream config;
    private final Map<SseEmitter, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final String boot = Long.toString(System.currentTimeMillis(), 36);
    private final ExecutorService sender;

    public SupportEventBroker(HelpProperties properties) {
        this.config = properties.getStream();
        this.sender = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10_000), runnable -> {
            Thread thread = new Thread(runnable, "support-events");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    public boolean enabled() {
        return config.isEnabled();
    }

    /** Stream of one visitor's support identity (all their routed conversations). */
    public SseEmitter visitor(Long guestId, Long sessionId) {
        long open = subscribers.values().stream().filter(subscriber -> guestId.equals(subscriber.guestId())).count();
        return open(new Subscriber(guestId, sessionId, null, null), open >= config.getMaxPerGuest());
    }

    /** Stream of a staff member: conversations of the given organization scope. */
    public SseEmitter staff(Long userId, OrganizationScope scope) {
        long open = subscribers.values().stream().filter(subscriber -> subscriber.staff() && Objects.equals(userId, subscriber.userId())).count();
        return open(new Subscriber(null, null, userId, scope), open >= config.getMaxPerUser());
    }

    private SseEmitter open(Subscriber subscriber, boolean overOwnLimit) {
        if (!config.isEnabled()) {
            throw new IllegalStateException("Support streams are disabled");
        }
        if (overOwnLimit || subscribers.size() >= config.getMaxTotal()) {
            throw new RateLimitExceededException(Duration.ofSeconds(30));
        }
        SseEmitter emitter = new SseEmitter(config.getTimeout().toMillis());
        subscribers.put(emitter, subscriber);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> {
            subscribers.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> subscribers.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").id(nextId()).reconnectTime(3_000)
                    .data(Map.of("heartbeatSeconds", config.getHeartbeat().toSeconds()), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            subscribers.remove(emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    /** A message or read state of this visitor's thread changed; sent after the surrounding transaction commits. */
    public void visitorEvent(Long guestId, Type type, Long messageId) {
        if (guestId == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type.name());
        data.put("messageId", messageId);
        afterCommit(() -> deliver("support", data, subscriber -> !subscriber.staff() && guestId.equals(subscriber.guestId())));
    }

    /** A conversation of {@code organizationId} (null = central support) changed; staff in scope reload it. */
    public void staffEvent(Long organizationId, Long conversationId, Type type) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type.name());
        data.put("conversationId", conversationId);
        afterCommit(() -> deliver("inbox", data, subscriber -> subscriber.staff() && subscriber.scope().includes(organizationId)));
    }

    /** This browser no longer has access ("forget this browser"): its open streams end. */
    public void closeSession(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        subscribers.entrySet().stream().filter(entry -> sessionId.equals(entry.getValue().sessionId())).map(Map.Entry::getKey)
                .toList().forEach(this::close);
    }

    /** Anonymous browser streams of a guest end (the thread was taken over by an account). */
    public void closeBrowsers(Long guestId) {
        subscribers.entrySet().stream().filter(entry -> guestId.equals(entry.getValue().guestId()) && entry.getValue().sessionId() != null)
                .map(Map.Entry::getKey).toList().forEach(this::close);
    }

    public int openStreams() {
        return subscribers.size();
    }

    @Scheduled(fixedDelayString = "${catalog.help.stream.heartbeat:PT20S}", initialDelayString = "${catalog.help.stream.heartbeat:PT20S}")
    public void heartbeat() {
        List<SseEmitter> emitters = List.copyOf(subscribers.keySet());
        if (emitters.isEmpty()) {
            return;
        }
        submit(() -> emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException exception) {
                drop(emitter);
            }
        }));
    }

    /** Ends every open stream (shutdown; tests). Clients reconnect and reload. */
    public void closeAll() {
        List.copyOf(subscribers.keySet()).forEach(this::close);
    }

    @PreDestroy
    void shutdown() {
        closeAll();
        sender.shutdownNow();
    }

    private void deliver(String name, Map<String, Object> data, java.util.function.Predicate<Subscriber> audience) {
        List<SseEmitter> targets = subscribers.entrySet().stream().filter(entry -> audience.test(entry.getValue()))
                .map(Map.Entry::getKey).toList();
        if (targets.isEmpty()) {
            return;
        }
        String id = nextId();
        submit(() -> targets.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name(name).id(id).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException exception) {
                drop(emitter);
            }
        }));
    }

    private void submit(Runnable task) {
        try {
            sender.execute(task);
        } catch (RejectedExecutionException exception) {
            log.debug("Support event dropped; clients repair it on their next reload");
        }
    }

    private void drop(SseEmitter emitter) {
        if (subscribers.remove(emitter) != null) {
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // Already completed by the container.
            }
        }
    }

    private void close(SseEmitter emitter) {
        subscribers.remove(emitter);
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // Already completed.
        }
    }

    private String nextId() {
        return boot + "-" + sequence.incrementAndGet();
    }

    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
