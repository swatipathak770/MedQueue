package com.medqueue.service;

import com.medqueue.event.QueueChangedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class QueueSnapshotService {
    private final QueueSnapshotReader snapshots;
    private final SimpMessagingTemplate messaging;
    public QueueSnapshotService(QueueSnapshotReader snapshots, SimpMessagingTemplate messaging) {
        this.snapshots = snapshots; this.messaging = messaging;
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(QueueChangedEvent event) {
        var update = snapshots.snapshot(event.doctorId(), event.date());
        messaging.convertAndSend("/topic/queue/" + event.doctorId(), update);
    }
}
