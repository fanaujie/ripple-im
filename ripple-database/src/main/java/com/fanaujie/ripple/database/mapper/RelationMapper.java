package com.fanaujie.ripple.database.mapper;

import com.fanaujie.ripple.database.model.RelationWithProfile;
import com.fanaujie.ripple.database.model.UserRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface RelationMapper {

    UserRelation findRelationBySourceAndTarget(
            @Param("sourceUserId") long sourceUserId, @Param("targetUserId") long targetUserId);

    void upsertRelationFlags(
            @Param("sourceUserId") long sourceUserId,
            @Param("targetUserId") long targetUserId,
            @Param("relationFlags") byte relationFlags);

    int updateDisplayName(
            @Param("sourceUserId") long sourceUserId,
            @Param("targetUserId") long targetUserId,
            @Param("displayName") String displayName);

    List<RelationWithProfile> findAllRelationsBySourceUserId(
            @Param("sourceUserId") long sourceUserId);
}
