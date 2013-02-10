package org.mailoverlord.server.controllers;

import org.mailoverlord.server.model.MessageJspData;
import org.mailoverlord.server.entities.Message;
import org.mailoverlord.server.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Index Controller
 */
@Controller
public class IndexController {

    private static final Logger logger = LoggerFactory.getLogger(IndexController.class);

    @Autowired
    private MessageRepository messageRepository;

    @RequestMapping("/")
    public String index(Pageable pageable, Model model) {
        logger.debug("PageAble: page number {}, page size {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<Message> page = messageRepository.findAll(pageable);
        model.addAttribute(new MessageJspData(page));
        return "index";
    }

}
