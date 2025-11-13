package org.ek.webcrawler.repository;

import org.ek.webcrawler.model.CrawlJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrawlJobRepository extends JpaRepository<CrawlJob, Long> {

    // Find all jobs with a specific status
    List<CrawlJob> findByStatus(CrawlJob.CrawlStatus status);

    //Find all jobs ordered by creation date by newest first
    List<CrawlJob> findAllByOrderByCreatedAtDesc();

    //Find jobs by start URL
    List<CrawlJob> findByStartUrl(String Url);


}
