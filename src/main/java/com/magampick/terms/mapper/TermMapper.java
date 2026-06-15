package com.magampick.terms.mapper;

import com.magampick.terms.domain.Term;
import com.magampick.terms.dto.TermResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TermMapper {

  TermResponse toResponse(Term term);
}
