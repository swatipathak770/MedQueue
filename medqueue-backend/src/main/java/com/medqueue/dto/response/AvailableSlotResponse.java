package com.medqueue.dto.response;
import com.medqueue.entity.Slot;
import java.time.LocalTime;
public record AvailableSlotResponse(Long id, Long doctorId, LocalTime startTime, LocalTime endTime,
        int slotCapacity, long remainingCapacity, boolean available) {
    public static AvailableSlotResponse from(Slot slot, long remaining, boolean available) {
        return new AvailableSlotResponse(slot.getId(), slot.getDoctor().getId(), slot.getStartTime(), slot.getEndTime(),
                slot.getSlotCapacity(), remaining, available && remaining > 0);
    }
}
