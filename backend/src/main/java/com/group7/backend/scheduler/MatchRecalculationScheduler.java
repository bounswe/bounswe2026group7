package com.group7.backend.scheduler;

import com.group7.backend.entity.MatchHistory;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.repository.MenteeRepository;
import com.group7.backend.repository.MentorRepository;
import com.group7.backend.repository.MatchHistoryRepository;
import com.group7.backend.service.MatchingService;
import com.group7.backend.service.NotificationEventPublisher;
import com.group7.backend.dto.response.MentorMatchResponse;
import com.group7.backend.dto.response.MenteeCandidateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Scheduler that recalculates top matches for all users daily.
 * Publishes a "match found" notification only when the top match changes.
 * This decouples notifications from read-only browse operations.
 */
@Component
public class MatchRecalculationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MatchRecalculationScheduler.class);

    private final MatchingService matchingService;
    private final MatchHistoryRepository matchHistoryRepository;
    private final MenteeRepository menteeRepository;
    private final MentorRepository mentorRepository;
    private final NotificationEventPublisher notificationEventPublisher;

    public MatchRecalculationScheduler(MatchingService matchingService,
                                      MatchHistoryRepository matchHistoryRepository,
                                      MenteeRepository menteeRepository,
                                      MentorRepository mentorRepository,
                                      NotificationEventPublisher notificationEventPublisher) {
        this.matchingService = matchingService;
        this.matchHistoryRepository = matchHistoryRepository;
        this.menteeRepository = menteeRepository;
        this.mentorRepository = mentorRepository;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    /**
     * Runs daily at 1:00 AM. Recalculates top matches and sends notifications only on change.
     */
    @Scheduled(cron = "${app.scheduler.match-recalculation.cron:0 0 1 * * *}")
    @Transactional
    public void recalculateMatches() {
        logger.info("Starting daily match recalculation");
        
        recalculateMenteeMatches();
        recalculateMentorMatches();
        
        logger.info("Completed daily match recalculation");
    }

    private void recalculateMenteeMatches() {
        List<Mentee> mentees = menteeRepository.findAll();
        logger.debug("Recalculating matches for {} mentees", mentees.size());

        for (Mentee mentee : mentees) {
            try {
                List<MentorMatchResponse> topMatches = matchingService.getTopMentorsForScheduler(mentee.getId());
                
                if (topMatches.isEmpty()) {
                    logger.debug("No matches found for mentee {}", mentee.getId());
                    continue;
                }

                MentorMatchResponse topMatch = topMatches.get(0);
                Optional<MatchHistory> lastHistory = matchHistoryRepository
                    .findLatestByUserIdAndUserType(mentee.getId(), MatchHistory.UserType.MENTEE);

                boolean isNewMatch = lastHistory.isEmpty() || 
                                   !lastHistory.get().getTopMatchId().equals(topMatch.getId());

                if (isNewMatch && lastHistory.isPresent()) {
                    logger.info("Top match changed for mentee {}. Sending notification.", mentee.getId());
                    notificationEventPublisher.publishMatchFound(mentee.getId(), topMatch.getFirstName());
                }

                MatchHistory history = new MatchHistory();
                history.setUserId(mentee.getId());
                history.setUserType(MatchHistory.UserType.MENTEE);
                history.setTopMatchId(topMatch.getId());
                history.setTopMatchName(topMatch.getFirstName());
                history.setMatchScore(topMatch.getMatchScore());
                matchHistoryRepository.save(history);

            } catch (Exception e) {
                logger.error("Error recalculating matches for mentee {}: {}", mentee.getId(), e.getMessage(), e);
            }
        }
    }

    private void recalculateMentorMatches() {
        List<Mentor> mentors = mentorRepository.findAll();
        logger.debug("Recalculating matches for {} mentors", mentors.size());

        for (Mentor mentor : mentors) {
            try {
                List<MenteeCandidateResponse> topCandidates = matchingService.getCandidateMenteesForScheduler(mentor.getId());
                
                if (topCandidates.isEmpty()) {
                    logger.debug("No candidates found for mentor {}", mentor.getId());
                    continue;
                }

                MenteeCandidateResponse topCandidate = topCandidates.get(0);
                Optional<MatchHistory> lastHistory = matchHistoryRepository
                    .findLatestByUserIdAndUserType(mentor.getId(), MatchHistory.UserType.MENTOR);

                boolean isNewMatch = lastHistory.isEmpty() || 
                                   !lastHistory.get().getTopMatchId().equals(topCandidate.getId());

                if (isNewMatch && lastHistory.isPresent()) {
                    logger.info("Top match changed for mentor {}. Sending notification.", mentor.getId());
                    notificationEventPublisher.publishMatchFound(mentor.getId(), topCandidate.getFirstName());
                }

                MatchHistory history = new MatchHistory();
                history.setUserId(mentor.getId());
                history.setUserType(MatchHistory.UserType.MENTOR);
                history.setTopMatchId(topCandidate.getId());
                history.setTopMatchName(topCandidate.getFirstName());
                history.setMatchScore(0); // Mentor-mentee matching doesn't have a numeric score in current implementation
                matchHistoryRepository.save(history);

            } catch (Exception e) {
                logger.error("Error recalculating matches for mentor {}: {}", mentor.getId(), e.getMessage(), e);
            }
        }
    }
}
