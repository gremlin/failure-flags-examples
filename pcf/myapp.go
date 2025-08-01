package main

import (
	"fmt"
	"net/http"
	"time"
)

func main() {
	for {
		start := time.Now()

		resp, err := http.Get("http://www.example.com")
		duration := time.Since(start)

		if err != nil {
			fmt.Printf("Request failed: %v (after %v)\n", err, duration)
		} else {
			fmt.Printf("Request to www.example.com - Status: %s | Duration: %v\n", resp.Status, duration)
			resp.Body.Close()
		}

		time.Sleep(5 * time.Second)
	}
}
