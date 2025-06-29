package com.team.teamreadioserver.post.entity;

import com.team.teamreadioserver.postReview.entity.PostReview;
import com.team.teamreadioserver.profile.entity.Profile;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@Table(name = "post")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private int postId;
    @Column(name = "post_title")
    private String postTitle;
    @Column(name = "post_content")
    private String postContent;
    @Column(name = "book_isbn")
    private String bookIsbn;
    @Column(name = "post_create_at")
    private Date postCreateDate;
    @Column(name = "reported_count")
    private int postReported;
    @Column(name = "is_hidden")
    private String postHidden;

    @ManyToOne
    @JoinColumn(name = "profile_id")
    private Profile profile;
    //    @ManyToOne
//    @JoinColumn(name = "profile_id")
    @PrePersist
    public void prePersist() {
        this.postCreateDate = new Date();
        this.postReported = 0;
        this.postHidden = "N";
    }

    @OneToOne(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private PostImg postImg;

    public void hide() {
        if (this.postHidden.equals("Y"))
            this.postHidden = "N";
        else
            this.postHidden = "Y";
    }

    public void hide2() {
        this.postHidden = "Y";
    }

//    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
//    private PostReview postReview;
}
