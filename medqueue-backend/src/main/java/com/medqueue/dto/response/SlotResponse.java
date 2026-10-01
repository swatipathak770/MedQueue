package com.medqueue.dto.response;
import com.medqueue.entity.Slot;
import java.time.DayOfWeek;
import java.time.LocalTime;
public record SlotResponse(Long id, Long doctorId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, int slotCapacity) {
    public static SlotResponse from(Slot s) { return new SlotResponse(s.getId(), s.getDoctor().getId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getSlotCapacity()); }
}
