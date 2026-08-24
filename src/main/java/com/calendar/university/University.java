package com.calendar.university;

import java.io.IOException;
import java.util.List;

public interface University {
    String getName();
    String getCode();
    List<String> fetchGroups() throws IOException;
    String buildScheduleUrl(String groupCode, int weekOffset);
}
