package com.yakmogo.yakmogo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
	// . (점)이 포함되지 않은 모든 경로(확장자가 없는 경로)를 index.html로 포워딩
	@GetMapping(value = {
		"/{path:[^\\.]*}",
		"/**/{path:[^\\.]*}"
	})
	public String forward() {
		return "forward:/index.html";
	}
}