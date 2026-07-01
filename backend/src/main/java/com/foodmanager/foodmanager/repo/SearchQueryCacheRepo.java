package com.foodmanager.foodmanager.repo;

import com.foodmanager.foodmanager.entity.SearchQueryCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SearchQueryCacheRepo extends JpaRepository<SearchQueryCache, Long> {
    Optional<SearchQueryCache> findByQueryKey(String queryKey);
}
