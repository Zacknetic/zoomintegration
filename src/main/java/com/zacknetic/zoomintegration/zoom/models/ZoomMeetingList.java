package com.zacknetic.zoomintegration.zoom.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response model for listing Zoom meetings (with pagination).
 *
 * Fellow Standards: Production-ready pagination model
 * Explicit: Clear pagination fields matching Zoom API
 *
 * Zoom API Reference: https://developers.zoom.us/docs/api/rest/reference/zoom-api/methods/#operation/meetings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZoomMeetingList {

    /**
     * Current page number (1-indexed)
     */
    @JsonProperty("page_number")
    private Integer pageNumber;

    /**
     * Number of records per page
     * Default: 30, Max: 300
     */
    @JsonProperty("page_size")
    private Integer pageSize;

    /**
     * Total number of pages
     */
    @JsonProperty("page_count")
    private Integer pageCount;

    /**
     * Total number of records across all pages
     */
    @JsonProperty("total_records")
    private Integer totalRecords;

    /**
     * Token for fetching next page
     * Production: Use this for cursor-based pagination
     */
    @JsonProperty("next_page_token")
    private String nextPageToken;

    /**
     * Array of meetings
     * Production: Can be empty list if no meetings
     */
    @JsonProperty("meetings")
    private List<ZoomMeeting> meetings;

    /**
     * Checks if there are more pages to fetch.
     * Production: Helper method for pagination logic
     *
     * @return true if more pages exist
     */
    public boolean hasNextPage() {
        if (pageNumber == null || pageCount == null) {
            return nextPageToken != null && !nextPageToken.isEmpty();
        }
        return pageNumber < pageCount;
    }

    /**
     * Checks if the result is empty.
     * Production: Helper method for user-friendly messaging
     *
     * @return true if no meetings found
     */
    public boolean isEmpty() {
        return meetings == null || meetings.isEmpty();
    }

    /**
     * Gets the number of meetings in this page.
     * Production: Helper method for logging and UI
     *
     * @return count of meetings
     */
    public int getCount() {
        return meetings != null ? meetings.size() : 0;
    }
}
