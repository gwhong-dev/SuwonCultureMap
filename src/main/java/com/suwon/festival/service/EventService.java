package com.suwon.festival.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.repository.EventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {

  private final EventRepository eventRepository;

  public List<EventResponse> getAllEvents() {
    return eventRepository.findByMergedIntoIdIsNull().stream()
        .map(event -> new EventResponse(
            event.getId(),
            event.getTitle(),
            event.getCategory(),
            event.getLatitude(),
            event.getLongitude(),
            event.getImageUrl(),
            event.getInfo(),
            event.getPubDate(),
            event.getStartDate(),
            event.getEndDate(),
            event.getLink()))

        .toList();
  }
}