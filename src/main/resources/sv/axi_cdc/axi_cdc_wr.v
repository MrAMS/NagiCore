/*

Copyright (c) 2019 Alex Forencich

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

*/

// Language: Verilog 2001

`resetall
`timescale 1ns / 1ps
`default_nettype none

/*
 * AXI4 clock domain crossing module (write)
 */
module axi_cdc_wr #
(
    // Width of data bus in bits
    parameter DATA_WIDTH = 32,
    // Width of address bus in bits
    parameter ADDR_WIDTH = 32,
    // Width of wstrb (width of data bus in words)
    parameter STRB_WIDTH = (DATA_WIDTH/8),
    // Width of ID
    parameter ID_WIDTH = 4
)
(
    /*
     * AXI slave interface
     */
    input  wire                   s_clk,
    input  wire                   s_rst,
    input  wire [ADDR_WIDTH-1:0]  s_axi_awaddr,

    input  wire [ID_WIDTH-1:0]    s_axi_awid,
    input  wire [8-1:0]           s_axi_awlen,
    input  wire [3-1:0]           s_axi_awsize,
    input  wire [2-1:0]           s_axi_awburst,

    input  wire [2:0]             s_axi_awprot,
    input  wire                   s_axi_awvalid,
    output wire                   s_axi_awready,
    
    input  wire [DATA_WIDTH-1:0]  s_axi_wdata,
    input  wire [STRB_WIDTH-1:0]  s_axi_wstrb,

    input  wire                   s_axi_wlast,

    input  wire                   s_axi_wvalid,
    output wire                   s_axi_wready,

    output wire [ID_WIDTH-1:0]    s_axi_bid,

    output wire [1:0]             s_axi_bresp,
    output wire                   s_axi_bvalid,
    input  wire                   s_axi_bready,

    /*
     * AXI master interface
     */
    input  wire                   m_clk,
    input  wire                   m_rst,
    output wire [ADDR_WIDTH-1:0]  m_axi_awaddr,

    output wire [ID_WIDTH-1:0]    m_axi_awid,
    output wire [8-1:0]           m_axi_awlen,
    output wire [3-1:0]           m_axi_awsize,
    output wire [2-1:0]           m_axi_awburst,

    output wire [2:0]             m_axi_awprot,
    output wire                   m_axi_awvalid,
    input  wire                   m_axi_awready,
    output wire [DATA_WIDTH-1:0]  m_axi_wdata,
    output wire [STRB_WIDTH-1:0]  m_axi_wstrb,

    output wire                   m_axi_wlast,

    output wire                   m_axi_wvalid,
    input  wire                   m_axi_wready,

    input  wire [ID_WIDTH-1:0]    m_axi_bid,

    input  wire [1:0]             m_axi_bresp,
    input  wire                   m_axi_bvalid,
    output wire                   m_axi_bready
);

reg [1:0] s_state_reg = 2'd0;
reg s_flag_reg = 1'b0;
(* srl_style = "register" *)
reg s_flag_sync_reg_1 = 1'b0;
(* srl_style = "register" *)
reg s_flag_sync_reg_2 = 1'b0;

reg [1:0] m_state_reg = 2'd0;
reg m_flag_reg = 1'b0;
(* srl_style = "register" *)
reg m_flag_sync_reg_1 = 1'b0;
(* srl_style = "register" *)
reg m_flag_sync_reg_2 = 1'b0;

reg [ADDR_WIDTH-1:0]  s_axi_awaddr_reg = {ADDR_WIDTH{1'b0}};

reg [ID_WIDTH-1:0]    s_axi_awid_reg = {ID_WIDTH{1'b0}};
reg [8-1:0]           s_axi_awlen_reg = {8{1'b0}};
reg [3-1:0]           s_axi_awsize_reg = {3{1'b0}};
reg [2-1:0]           s_axi_awburst_reg = {2{1'b0}};

reg [2:0]             s_axi_awprot_reg = 3'd0;
reg                   s_axi_awvalid_reg = 1'b0;
reg [DATA_WIDTH-1:0]  s_axi_wdata_reg = {DATA_WIDTH{1'b0}};
reg [STRB_WIDTH-1:0]  s_axi_wstrb_reg = {STRB_WIDTH{1'b0}};

reg                   s_axi_wlast_reg = 0;

reg                   s_axi_wvalid_reg = 1'b0;

reg [ID_WIDTH-1:0]    s_axi_bid_reg = {ID_WIDTH{1'b0}};

reg [1:0]             s_axi_bresp_reg = 2'b00;
reg                   s_axi_bvalid_reg = 1'b0;

reg [ADDR_WIDTH-1:0]  m_axi_awaddr_reg = {ADDR_WIDTH{1'b0}};

reg [ID_WIDTH-1:0]    m_axi_awid_reg = {ID_WIDTH{1'b0}};
reg [8-1:0]           m_axi_awlen_reg = {8{1'b0}};
reg [3-1:0]           m_axi_awsize_reg = {3{1'b0}};
reg [2-1:0]           m_axi_awburst_reg = {2{1'b0}};

reg [2:0]             m_axi_awprot_reg = 3'd0;
reg                   m_axi_awvalid_reg = 1'b0;
reg [DATA_WIDTH-1:0]  m_axi_wdata_reg = {DATA_WIDTH{1'b0}};
reg [STRB_WIDTH-1:0]  m_axi_wstrb_reg = {STRB_WIDTH{1'b0}};

reg                   m_axi_wlast_reg = 0;

reg                   m_axi_wvalid_reg = 1'b0;

reg [ID_WIDTH-1:0]    m_axi_bid_reg = {ID_WIDTH{1'b0}};

reg [1:0]             m_axi_bresp_reg = 2'b00;
reg                   m_axi_bvalid_reg = 1'b1;

assign s_axi_awready = !s_axi_awvalid_reg && !s_axi_bvalid_reg;
assign s_axi_wready = !s_axi_wvalid_reg && !s_axi_bvalid_reg;

assign s_axi_bid = s_axi_bid_reg;

assign s_axi_bresp = s_axi_bresp_reg;
assign s_axi_bvalid = s_axi_bvalid_reg;

assign m_axi_awaddr = m_axi_awaddr_reg;

assign m_axi_awid = m_axi_awid_reg;
assign m_axi_awlen = m_axi_awlen_reg;
assign m_axi_awsize = m_axi_awsize_reg;
assign m_axi_awburst = m_axi_awburst_reg;

assign m_axi_awprot = m_axi_awprot_reg;
assign m_axi_awvalid = m_axi_awvalid_reg;
assign m_axi_wdata = m_axi_wdata_reg;
assign m_axi_wstrb = m_axi_wstrb_reg;

assign m_axi_wlast = m_axi_wlast_reg;

assign m_axi_wvalid = m_axi_wvalid_reg;
assign m_axi_bready = !m_axi_bvalid_reg;

// slave side
always @(posedge s_clk) begin
    s_axi_bvalid_reg <= s_axi_bvalid_reg && !s_axi_bready;

    if (!s_axi_awvalid_reg && !s_axi_bvalid_reg) begin
        s_axi_awaddr_reg <= s_axi_awaddr;

        s_axi_awid_reg <= s_axi_awid;
        s_axi_awlen_reg <= s_axi_awlen;
        s_axi_awsize_reg <= s_axi_awsize;
        s_axi_awburst_reg <= s_axi_awburst;

        s_axi_awprot_reg <= s_axi_awprot;
        s_axi_awvalid_reg <= s_axi_awvalid;
    end

    if (!s_axi_wvalid_reg && !s_axi_bvalid_reg) begin
        s_axi_wdata_reg <= s_axi_wdata;
        s_axi_wstrb_reg <= s_axi_wstrb;

        s_axi_wlast_reg <= s_axi_wlast;

        s_axi_wvalid_reg <= s_axi_wvalid;
    end

    case (s_state_reg)
        2'd0: begin
            if (s_axi_awvalid_reg && s_axi_wvalid_reg) begin
                s_state_reg <= 2'd1;
                s_flag_reg <= 1'b1;
            end
        end
        2'd1: begin
            if (m_flag_sync_reg_2) begin
                s_state_reg <= 2'd2;
                s_flag_reg <= 1'b0;

                s_axi_bid_reg <= m_axi_bid_reg;

                s_axi_bresp_reg <= m_axi_bresp_reg;
                s_axi_bvalid_reg <= 1'b1;
            end
        end
        2'd2: begin
            if (!m_flag_sync_reg_2) begin
                s_state_reg <= 2'd0;
                s_axi_awvalid_reg <= 1'b0;
                s_axi_wvalid_reg <= 1'b0;
            end
        end
    endcase

    if (s_rst) begin
        s_state_reg <= 2'd0;
        s_flag_reg <= 1'b0;
        s_axi_awvalid_reg <= 1'b0;
        s_axi_wvalid_reg <= 1'b0;
        s_axi_bvalid_reg <= 1'b0;
    end
end

// synchronization
always @(posedge s_clk) begin
    m_flag_sync_reg_1 <= m_flag_reg;
    m_flag_sync_reg_2 <= m_flag_sync_reg_1;
end

always @(posedge m_clk) begin
    s_flag_sync_reg_1 <= s_flag_reg;
    s_flag_sync_reg_2 <= s_flag_sync_reg_1;
end

// master side
always @(posedge m_clk) begin
    m_axi_awvalid_reg <= m_axi_awvalid_reg && !m_axi_awready;
    m_axi_wvalid_reg <= m_axi_wvalid_reg && !m_axi_wready;

    if (!m_axi_bvalid_reg) begin
        m_axi_bid_reg <= m_axi_bid;

        m_axi_bresp_reg <= m_axi_bresp;
        m_axi_bvalid_reg <= m_axi_bvalid;
    end

    case (m_state_reg)
        2'd0: begin
            if (s_flag_sync_reg_2) begin
                m_state_reg <= 2'd1;
                m_axi_awaddr_reg <= s_axi_awaddr_reg;

                m_axi_awid_reg <= s_axi_awid_reg;
                m_axi_awlen_reg <= s_axi_awlen_reg;
                m_axi_awsize_reg <= s_axi_awsize_reg;
                m_axi_awburst_reg <= s_axi_awburst_reg;

                m_axi_awprot_reg <= s_axi_awprot_reg;
                m_axi_awvalid_reg <= 1'b1;
                m_axi_wdata_reg <= s_axi_wdata_reg;
                m_axi_wstrb_reg <= s_axi_wstrb_reg;

                m_axi_wlast_reg <= s_axi_wlast_reg;

                m_axi_wvalid_reg <= 1'b1;

                m_axi_bid_reg <= s_axi_bid_reg;

                m_axi_bvalid_reg <= 1'b0;
            end
        end
        2'd1: begin
            if (m_axi_bvalid_reg) begin
                m_flag_reg <= 1'b1;
                m_state_reg <= 2'd2;
            end
        end
        2'd2: begin
            if (!s_flag_sync_reg_2) begin
                m_state_reg <= 2'd0;
                m_flag_reg <= 1'b0;
            end
        end
    endcase

    if (m_rst) begin
        m_state_reg <= 2'd0;
        m_flag_reg <= 1'b0;
        m_axi_awvalid_reg <= 1'b0;
        m_axi_wvalid_reg <= 1'b0;
        m_axi_bvalid_reg <= 1'b1;
    end
end

endmodule

`resetall
