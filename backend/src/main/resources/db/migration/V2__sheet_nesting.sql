-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
create table sheet_job (
 id bigint auto_increment primary key,
 reference varchar(60) not null,
 name varchar(160) not null,
 department_id bigint not null,
 category varchar(60) not null,
 material varchar(160) not null,
 width_ticks int not null,
 height_ticks int not null,
 margin_ticks int not null,
 kerf_ticks int not null,
 max_sheets int not null,
 instructions varchar(1000) not null,
 reviewer_id bigint not null,
 operator_id bigint not null,
 created_by bigint not null,
 status varchar(30) not null,
 active_revision_id bigint null,
 approved_hash varchar(64) null,
 report_hash varchar(64) null,
 closed_hash varchar(64) null,
 requested_outcome varchar(30) null,
 outcome varchar(30) null,
 acknowledged_at timestamp(6) null,
 approved_at timestamp(6) null,
 closed_at timestamp(6) null,
 created_at timestamp(6) not null,
 version bigint not null,
 UNIQUE(reference),
 FOREIGN KEY(department_id) REFERENCES department(id),
 FOREIGN KEY(reviewer_id) REFERENCES account(id),
 FOREIGN KEY(operator_id) REFERENCES account(id),
 FOREIGN KEY(created_by) REFERENCES account(id),
 CHECK(width_ticks BETWEEN 10 AND 500000 AND height_ticks BETWEEN 10 AND 500000),
 CHECK(margin_ticks >= 0 AND margin_ticks*2 < width_ticks AND margin_ticks*2 < height_ticks),
 CHECK(kerf_ticks BETWEEN 0 AND 1000),
 CHECK(max_sheets BETWEEN 1 AND 30),
 INDEX ix_job_scope(department_id,status)
);
create table nest_part (
 id bigint auto_increment primary key,
 job_id bigint not null,
 code varchar(60) not null,
 name varchar(160) not null,
 width_ticks int not null,
 height_ticks int not null,
 quantity int not null,
 rotation boolean not null,
 FOREIGN KEY(job_id) REFERENCES sheet_job(id),
 UNIQUE(job_id,code),
 CHECK(width_ticks BETWEEN 10 AND 500000 AND height_ticks BETWEEN 10 AND 500000),
 CHECK(quantity BETWEEN 1 AND 300)
);
create table nest_revision (
 id bigint auto_increment primary key,
 job_id bigint not null,
 algorithm varchar(80) not null,
 input_hash varchar(64) not null,
 plan_hash varchar(64) not null,
 input_json longtext not null,
 plan_json longtext not null,
 created_by bigint not null,
 created_at timestamp(6) not null,
 FOREIGN KEY(job_id) REFERENCES sheet_job(id),
 FOREIGN KEY(created_by) REFERENCES account(id),
 INDEX ix_revision_job(job_id,id)
);
create table job_editor (
 id bigint auto_increment primary key,
 job_id bigint not null,
 actor_id bigint not null,
 FOREIGN KEY(job_id) REFERENCES sheet_job(id),
 FOREIGN KEY(actor_id) REFERENCES account(id),
 UNIQUE(job_id,actor_id)
);
create table cut_result (
 id bigint auto_increment primary key,
 job_id bigint not null,
 part_id bigint not null,
 good int not null,
 scrap int not null,
 not_cut int not null,
 note varchar(1000) not null,
 actor_id bigint not null,
 recorded_at timestamp(6) not null,
 FOREIGN KEY(job_id) REFERENCES sheet_job(id),
 FOREIGN KEY(part_id) REFERENCES nest_part(id),
 FOREIGN KEY(actor_id) REFERENCES account(id),
 UNIQUE(job_id,part_id),
 CHECK(good >= 0 AND scrap >= 0 AND not_cut >= 0)
);
alter table sheet_job add constraint fk_job_revision foreign key(active_revision_id) references nest_revision(id);
create table command_record (id bigint auto_increment primary key,request_key varchar(36) not null unique,fingerprint varchar(64) not null,response_json longtext not null);
create table business_event (id bigint auto_increment primary key,object_type varchar(30) not null,object_id bigint not null,actor_id bigint not null,action varchar(60) not null,note varchar(1000) not null,snapshot longtext not null,created_at timestamp(6) not null);
alter table business_event add constraint fk_business_event_actor_id foreign key (actor_id) references account(id);
create index idx_event_obj on business_event(object_type,object_id);
