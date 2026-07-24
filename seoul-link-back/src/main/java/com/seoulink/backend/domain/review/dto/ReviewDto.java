package com.seoulink.backend.domain.review.dto;

/**
 * 도메인 데이터를 전달하기 위한 DTO입니다.
 */
public class ReviewDto {
    private Long id;
    private String title;
    private String content;
    private String placeName;
    private int rating;
    private int likeCount;

    public ReviewDto() {
    }

    public ReviewDto(Long id, String title, String content, String placeName, int rating, int likeCount) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.placeName = placeName;
        this.rating = rating;
        this.likeCount = likeCount;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getPlaceName() {
        return placeName;
    }

    public int getRating() {
        return rating;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }
}