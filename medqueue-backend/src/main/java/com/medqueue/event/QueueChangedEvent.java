package com.medqueue.event;
import java.time.LocalDate;
public record QueueChangedEvent(Long doctorId, LocalDate date) { }
